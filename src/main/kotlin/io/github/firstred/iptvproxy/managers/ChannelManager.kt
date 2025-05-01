package io.github.firstred.iptvproxy.managers

import io.github.firstred.iptvproxy.config
import io.github.firstred.iptvproxy.db.repositories.ChannelRepository
import io.github.firstred.iptvproxy.db.repositories.EpgRepository
import io.github.firstred.iptvproxy.db.repositories.XtreamRepository
import io.github.firstred.iptvproxy.di.modules.IptvServersByName
import io.github.firstred.iptvproxy.dtos.m3u.M3uChannel
import io.github.firstred.iptvproxy.dtos.m3u.M3uDoc
import io.github.firstred.iptvproxy.dtos.xmltv.XmltvChannel
import io.github.firstred.iptvproxy.dtos.xmltv.XmltvDoc
import io.github.firstred.iptvproxy.dtos.xmltv.XmltvUtils
import io.github.firstred.iptvproxy.dtos.xtream.XtreamLiveStream
import io.github.firstred.iptvproxy.dtos.xtream.XtreamLiveStreamCategory
import io.github.firstred.iptvproxy.dtos.xtream.XtreamMovie
import io.github.firstred.iptvproxy.dtos.xtream.XtreamMovieCategory
import io.github.firstred.iptvproxy.dtos.xtream.XtreamSeries
import io.github.firstred.iptvproxy.dtos.xtream.XtreamSeriesCategory
import io.github.firstred.iptvproxy.entities.IptvChannel
import io.github.firstred.iptvproxy.entities.IptvServerConnection
import io.github.firstred.iptvproxy.entities.IptvUser
import io.github.firstred.iptvproxy.enums.IptvChannelType
import io.github.firstred.iptvproxy.events.ChannelsAreAvailableEvent
import io.github.firstred.iptvproxy.listeners.hooks.HasOnApplicationEventHook
import io.github.firstred.iptvproxy.listeners.hooks.lifecycle.HasApplicationOnDatabaseInitializedHook
import io.github.firstred.iptvproxy.listeners.hooks.lifecycle.HasApplicationOnTerminateHook
import io.github.firstred.iptvproxy.parsers.M3uParser
import io.github.firstred.iptvproxy.utils.channelType
import io.github.firstred.iptvproxy.utils.dispatchHook
import io.github.firstred.iptvproxy.utils.forwardProxyUser
import io.github.firstred.iptvproxy.utils.hash
import io.github.firstred.iptvproxy.utils.sendUserAgent
import io.github.firstred.iptvproxy.utils.toProxiedIconUrl
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.jvm.javaio.*
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.InputStream
import java.io.OutputStream
import java.net.URI
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import kotlin.text.Charsets.UTF_8

class ChannelManager : KoinComponent, HasApplicationOnTerminateHook, HasApplicationOnDatabaseInitializedHook {
    private val serversByName: IptvServersByName by inject()
    private val scheduledExecutorService: ScheduledExecutorService by inject()
    private val httpClient: HttpClient by inject()
    private val channelRepository: ChannelRepository by inject()
    private val epgRepository: EpgRepository by inject()
    private val xtreamRepository: XtreamRepository by inject()

    private suspend fun updateChannels() {
        LOG.info("Updating channels")

        val newChannels: MutableMap<String, IptvChannel> = mutableMapOf()
        fun addNewChannel(reference: String, channel: IptvChannel) {
            newChannels[reference] = channel
        }

        if (serversByName.isEmpty()) throw RuntimeException("No servers configured")

        serversByName.keys.forEach { channelRepository.signalPlaylistStartedForServer(it) }

        for (server in serversByName.values) {
            var xmltv: XmltvDoc? = null
            if (server.config.epgUrl != null) {
                LOG.info("Waiting for xmltv data to be downloaded")

                epgRepository.signalXmltvStartedForServer(server.name)

                server.withConnection(
                    config.timeouts.playlist.totalMilliseconds,
                    server.config.accounts?.first(),
                ) { serverConnection, _ ->
                    LOG.info("Parsing xmltv data")
                    loadXmltv(serverConnection).let { inputStream ->
                        inputStream.use { xmltv = XmltvUtils.parseXmltv(it) }
                    }
                }

                val endOf = Clock.System.now() + server.config.epgAfter
                val startOf = Clock.System.now() - server.config.epgBefore
                xmltv?.let {
                    epgRepository.upsertXmltvSourceForServer(
                        xmltv.copy(
                            programmes = it.programmes?.filter { programme ->
                                (programme.start < endOf) && (programme.stop > startOf)
                            }?.toMutableList()
                        ),
                        server.name
                    )
                }

                epgRepository.signalXmltvCompletedForServer(server.name)
            }

            val xmltvById: MutableMap<String, XmltvChannel> = mutableMapOf()
            val xmltvByName: MutableMap<String, XmltvChannel> = mutableMapOf()

            for (account in server.config.accounts ?: emptyList()) {
                LOG.info("Parsing playlist: {}, url: {}", server.name, account.url)

                lateinit var channelsInputStream: InputStream
                lateinit var m3u: M3uDoc

                server.withConnection(
                    config.timeouts.playlist.totalMilliseconds,
                    account,
                ) { serverConnection, _ ->
                    channelsInputStream = loadChannels(serverConnection)
                    m3u = M3uParser.parse(channelsInputStream) ?: throw RuntimeException("Error parsing m3u")
                }

                m3u.channels.forEach { m3uChannel: M3uChannel ->
                    val channelReference = (server.name + "||" + m3uChannel.url).hash()

                    addNewChannel(channelReference, newChannels[channelReference] ?: let {
                        val tvgId: String = m3uChannel.props["tvg-id"] ?: ""
                        val tvgName: String = m3uChannel.props["tvg-name"] ?: ""

                        if (server.config.groupFilters.isNotEmpty()) {
                            if (m3uChannel.groups.stream().noneMatch { group: String ->
                                    server.config.groupFilters.stream()
                                        .anyMatch { f: Pattern? -> f!!.matcher(group).find() }
                                }
                            ) {
                                // skip channel - filtered by group filter
                                return@forEach
                            }
                        }

                        var xmltvCh: XmltvChannel? = null
                        if (tvgId.isNotBlank()) xmltvCh = xmltvById[tvgId]
                        if (xmltvCh == null && tvgName.isNotBlank()) xmltvCh = xmltvByName[tvgName]
                        if (xmltvCh == null && tvgName.isNotBlank()) xmltvCh =
                            xmltvByName[tvgName.replace(' ', '_')]
                        if (xmltvCh == null) xmltvCh = xmltvByName[m3uChannel.name]
                        if (xmltvCh == null) xmltvCh = xmltvByName[m3uChannel.name.replace(' ', '_')]

                        // Redirect logo URI
                        val logo = (xmltvCh?.icon?.src ?: m3uChannel.props["tvg-logo"])

                        var days = 0
                        var daysStr = m3uChannel.props["tvg-rec"] ?: ""
                        if (daysStr.isBlank()) daysStr = m3uChannel.props["catchup-days"] ?: ""
                        if (daysStr.isNotBlank()) {
                            try {
                                days = daysStr.toInt()
                            } catch (_: NumberFormatException) {
                                LOG.warn("Error parsing catchup days: {}, channel: {}", daysStr, m3uChannel.name)
                            }
                        }

                        IptvChannel(
                            name = m3uChannel.name,
                            logo = logo,
                            groups = m3uChannel.groups,
                            epgId = m3uChannel.props["tvg-id"],
                            catchupDays = days,
                            url = URI(m3uChannel.url),
                            server = server,
                            type = m3uChannel.url.channelType(),
                        )
                    })
                }

                if (account.isXtream()) {
                    xtreamRepository.signalXtreamStartedForServer(server.name)

                    // Update xtream info
                    // Load live streams
                    lateinit var liveStreamCategories: List<XtreamLiveStreamCategory>
                    lateinit var liveStreams: List<XtreamLiveStream>
                    server.withConnection(
                        config.timeouts.playlist.totalMilliseconds,
                        account,
                    ) { serverConnection, _ ->
                        liveStreamCategories = httpClient.get(
                            serverConnection.config.account.getXtreamLiveStreamCategoriesUrl().toString()
                        ) {
                            headers {
                                accept(ContentType.Application.Json)
                                forwardProxyUser(serverConnection.config)
                                sendUserAgent(serverConnection.config)
                            }
                        }.body()
                    }
                    server.withConnection(
                        config.timeouts.playlist.totalMilliseconds,
                        account,
                    ) { serverConnection, _ ->
                        liveStreams = httpClient.get(serverConnection.config.account.getXtreamLiveStreamsUrl().toString()) {
                            headers {
                                accept(ContentType.Application.Json)
                                forwardProxyUser(serverConnection.config)
                                sendUserAgent(serverConnection.config)
                            }
                        }.body()
                    }
                    xtreamRepository.upsertLiveStreamsAndCategories(liveStreams, liveStreamCategories, server.name)

                    // Load movies
                    lateinit var movieCategories: List<XtreamMovieCategory>
                    lateinit var movies: List<XtreamMovie>
                    server.withConnection(
                        config.timeouts.playlist.totalMilliseconds,
                        account,
                    ) { serverConnection, _ ->
                        movieCategories = httpClient.get(serverConnection.config.account.getXtreamMovieCategoriesUrl().toString()) {
                            headers {
                                forwardProxyUser(serverConnection.config)
                                sendUserAgent(serverConnection.config)
                            }
                        }.body<List<XtreamMovieCategory>>()
                    }
                    server.withConnection(
                        config.timeouts.playlist.totalMilliseconds,
                        account,
                    ) { serverConnection, _ ->
                        movies = httpClient.get(serverConnection.config.account.getXtreamMoviesUrl().toString()) {
                            headers {
                                forwardProxyUser(serverConnection.config)
                                sendUserAgent(serverConnection.config)
                            }
                        }.body<List<XtreamMovie>>()
                    }
                    xtreamRepository.upsertMoviesAndCategories(movies, movieCategories, server.name)

                    // Load tv series
                    lateinit var seriesCategories: List<XtreamSeriesCategory>
                    lateinit var series: List<XtreamSeries>
                    server.withConnection(
                        config.timeouts.playlist.totalMilliseconds,
                        account,
                    ) { serverConnection, _ ->
                        seriesCategories = httpClient.get(serverConnection.config.account.getXtreamSeriesCategoriesUrl().toString()) {
                            headers {
                                forwardProxyUser(serverConnection.config)
                                sendUserAgent(serverConnection.config)
                            }
                        }.body<List<XtreamSeriesCategory>>()
                    }
                    server.withConnection(
                        config.timeouts.playlist.totalMilliseconds,
                        account,
                    ) { serverConnection, _ ->
                        series = httpClient.get(serverConnection.config.account.getXtreamSeriesUrl().toString()) {
                            headers {
                                forwardProxyUser(serverConnection.config)
                                sendUserAgent(serverConnection.config)
                            }
                        }.body<List<XtreamSeries>>()
                    }
                    xtreamRepository.upsertSeriesAndCategories(series, seriesCategories, server.name)

                    xtreamRepository.signalXtreamCompletedForServer(server.name)
                }
            }
        }

        channelRepository.upsertChannels(newChannels.values.toList())

        serversByName.keys.forEach { channelRepository.signalPlaylistCompletedForServer(it) }

        val channelCount = channelRepository.getIptvChannelCount()

        // Signal channels are updated
        if (channelCount > 0) dispatchHook(HasOnApplicationEventHook::class, ChannelsAreAvailableEvent())

        LOG.info("{} channels updated", channelCount)
    }

    private suspend fun loadXmltv(serverConnection: IptvServerConnection): InputStream {
        val response = httpClient.get(serverConnection.config.getEpgUrl().toString()) {
            headers {
                forwardProxyUser(serverConnection.config)
                sendUserAgent(serverConnection.config)
            }
        }
        return response.bodyAsChannel().toInputStream()
    }

    private suspend fun loadChannels(serverConnection: IptvServerConnection): InputStream {
        val response = httpClient.get(serverConnection.config.account.getPlaylistUrl().toString()) {
            headers {
                forwardProxyUser(serverConnection.config)
                sendUserAgent(serverConnection.config)
            }
        }
        return response.bodyAsChannel().toInputStream()
    }

    suspend fun getChannelPlaylist(
        channelId: Long,
        user: IptvUser,
        baseUrl: URI,
        additionalHeaders: Headers = headersOf(),
        additionalQueryParameters: Parameters = parametersOf(),
        headersCallback: ((Headers) -> Unit)? = null,
    ): String {
        return (channelRepository.getChannelById(channelId)
        ?: run { throw RuntimeException("Channel not found") }).getPlaylist(user, baseUrl, additionalHeaders, additionalQueryParameters, headersCallback)
    }

    suspend fun getLiveStreamsPlaylist(
        outputStream: OutputStream,
        user: IptvUser,
        baseUrl: URI,
    ) {
        val outputWriter = outputStream.bufferedWriter(UTF_8)
        val encryptedAccount = user.toEncryptedAccountHexString()

        outputWriter.write("#EXTM3U\n")

        channelRepository.forEachIptvChannelChunk { chunk ->
            chunk.filterNot { null == it.id }.forEach { channel: IptvChannel ->
                outputWriter.write("#EXTINF:-1")
                outputWriter.write(" tvg-id=\"")
                outputWriter.write(channel.epgId ?: "")
                outputWriter.write("\"")

                outputWriter.write(" tvg-logo=\"")
                channel.logo?.let {
                    if ("null" != channel.logo) {
                        if (channel.server.config.proxyStream) {
                            outputWriter.write(channel.logo.toProxiedIconUrl(baseUrl, encryptedAccount))
                        } else {
                            outputWriter.write(channel.logo)
                        }
                    }
                }
                outputWriter.write("\"")

                if (channel.catchupDays > 0) {
                    outputWriter.write(" catchup=\"shift\" catchup-days=\"")
                    outputWriter.write(channel.catchupDays.toString())
                    outputWriter.write("\"")
                }

                if (channel.groups.isNotEmpty()) {
                    outputWriter.write(" group-title=\"")
                    outputWriter.write(channel.groups.first())
                    outputWriter.write("\"")
                }

                outputWriter.write(",")
                outputWriter.write(channel.name)
                outputWriter.write("\n")

                if (channel.groups.isNotEmpty()) {
                    outputWriter.write("#EXTGRP:")
                    outputWriter.write(java.lang.String.join(";", channel.groups))
                    outputWriter.write("\n")
                }

                if (channel.server.config.proxyStream) {
                    if (channel.type == IptvChannelType.live) {
                        outputWriter.write(baseUrl.resolve("${channel.type.urlType()}/${user.username}/${user.password}/${channel.id}.m3u8").toString())
                    } else {
                        outputWriter.write(baseUrl.resolve("${channel.type.urlType()}/${user.username}/${user.password}/${channel.id}.${channel.url.toString().substringAfterLast('.')}").toString())
                    }
                } else {
                    outputWriter.write(channel.url.toString())
                }
                outputWriter.write("\n")
                outputWriter.flush()
            }
        }
    }

    private fun scheduleChannelCleanups(delay: Long =0, unit: TimeUnit = TimeUnit.MINUTES) {
        scheduledExecutorService.schedule(
            Thread {
                try {
                    runBlocking {
                        channelRepository.cleanup()
                        epgRepository.cleanup()
                        xtreamRepository.cleanup()
                        scheduleChannelCleanups(config.cleanupInterval.inWholeMinutes)
                    }
                } catch (e: InterruptedException) {
                    LOG.info("Scheduler interrupted while cleaning channels", e)
                } catch (e: Exception) {
                    LOG.error("Error while cleaning channels", e)
                    runBlocking {
                        scheduleChannelCleanups(config.cleanupInterval.inWholeMinutes)
                    }
                }
            },
            delay,
            unit,
        )
    }

    private fun scheduleChannelUpdates(delay: Long = 0, unit: TimeUnit = TimeUnit.MINUTES) {
        scheduledExecutorService.schedule(
            Thread {
                try {
                    runBlocking {
                        updateChannels()
                        scheduleChannelUpdates(config.updateInterval.inWholeMinutes)
                    }
                } catch (e: InterruptedException) {
                    LOG.info("Scheduler interrupted while updating channels", e)
                } catch (e: Exception) {
                    LOG.error("Error while updating channels", e)
                    runBlocking {
                        scheduleChannelUpdates(config.updateIntervalOnFailure.inWholeMinutes)
                    }
                }
            },
            delay,
            unit,
        )
    }

    override fun onApplicationDatabaseInitializedHook() {
        LOG.info("Channel manager starting")
        scheduleChannelUpdates()
        scheduleChannelCleanups()
        LOG.info("Channel manager started")

        val channelCount = channelRepository.getIptvChannelCount()
        LOG.info("Channel count: $channelCount")
        if (channelCount > 0) {
            dispatchHook(HasOnApplicationEventHook::class, ChannelsAreAvailableEvent())
        }
    }

    override fun onApplicationTerminateHook() {
        LOG.info("Channel manager stopping")

        try {
            scheduledExecutorService.shutdownNow()
            if (!scheduledExecutorService.awaitTermination(1, TimeUnit.MINUTES)) {
                LOG.warn("Scheduler is still running...")
                scheduledExecutorService.shutdownNow()
            }
        } catch (_: InterruptedException) {
            LOG.error("Interrupted while stopping scheduler")
        }

        LOG.info("Channel manager stopped")
    }

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(ChannelManager::class.java)
    }
}
