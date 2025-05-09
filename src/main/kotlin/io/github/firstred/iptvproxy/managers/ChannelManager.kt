package io.github.firstred.iptvproxy.managers

import io.github.firstred.iptvproxy.classes.IptvChannel
import io.github.firstred.iptvproxy.classes.IptvServerConnection
import io.github.firstred.iptvproxy.classes.IptvUser
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
import io.github.firstred.iptvproxy.dtos.xtream.XtreamCategory
import io.github.firstred.iptvproxy.dtos.xtream.XtreamLiveStream
import io.github.firstred.iptvproxy.dtos.xtream.XtreamMovie
import io.github.firstred.iptvproxy.dtos.xtream.XtreamSeries
import io.github.firstred.iptvproxy.enums.IptvChannelType
import io.github.firstred.iptvproxy.events.ChannelsAreAvailableEvent
import io.github.firstred.iptvproxy.listeners.hooks.HasOnApplicationEventHook
import io.github.firstred.iptvproxy.listeners.hooks.lifecycle.HasApplicationOnDatabaseInitializedHook
import io.github.firstred.iptvproxy.listeners.hooks.lifecycle.HasApplicationOnTerminateHook
import io.github.firstred.iptvproxy.parsers.M3uParser
import io.github.firstred.iptvproxy.utils.addDefaultClientHeaders
import io.github.firstred.iptvproxy.utils.dispatchHook
import io.github.firstred.iptvproxy.utils.hash
import io.github.firstred.iptvproxy.utils.toChannelType
import io.github.firstred.iptvproxy.utils.toEncodedJavaURI
import io.github.firstred.iptvproxy.utils.toProxiedIconUrl
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.jvm.javaio.*
import io.sentry.MonitorConfig
import io.sentry.Sentry
import io.sentry.util.CheckInUtils
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import org.apache.commons.io.input.buffer.PeekableInputStream
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.qualifier.named
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.BufferedInputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.URI
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import java.util.zip.GZIPInputStream
import kotlin.text.Charsets.UTF_8

@Suppress("UnstableApiUsage")
class ChannelManager : KoinComponent, HasApplicationOnTerminateHook, HasApplicationOnDatabaseInitializedHook {
    private val serversByName: IptvServersByName by inject()
    private val scheduledExecutorService: ScheduledExecutorService by inject()
    private val httpClient: HttpClient by inject()
    private val channelRepository: ChannelRepository by inject()
    private val epgRepository: EpgRepository by inject()
    private val xtreamRepository: XtreamRepository by inject()
    private val updateMonitorConfig: MonitorConfig by inject(named("update-channels"))
    private val cleanupMonitorConfig: MonitorConfig by inject(named("cleanup-channels"))
    private val databaseMutex: Mutex by inject(named("large-database-transactions"))

    private suspend fun updateChannels()
    {
        LOG.info("Updating channels")

        if (serversByName.isEmpty()) throw RuntimeException("No servers configured")

        serversByName.keys.forEach { channelRepository.signalPlaylistImportStartedForServer(it) }

        for (server in serversByName.values) {
            val newChannels: MutableMap<String, IptvChannel> = mutableMapOf()
            fun addNewChannel(reference: String, channel: IptvChannel) {
                newChannels[reference] = channel
            }

            var xmltv: XmltvDoc? = null
            if (server.config.epgUrl != null) {
                LOG.info("Waiting for xmltv data to be downloaded")

                epgRepository.signalXmltvImportStartedForServer(server.name)

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
                            }?.toMutableList(),
                        ),
                        server.name
                    )
                }

                epgRepository.signalXmltvImportCompletedForServer(server.name)
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
                var externalIndex = 0u

                m3u.channels.forEach { m3uChannel: M3uChannel ->
                    val channelReference = (server.name + "||" + m3uChannel.url).hash()

                    addNewChannel(channelReference, newChannels[channelReference] ?: let {
                        val tvgId: String = server.config.epgRemapping[(m3uChannel.props["tvg-id"] ?: "")] ?: m3uChannel.props["tvg-id"] ?: ""
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
                        if (xmltvCh == null && tvgName.isNotBlank()) xmltvCh = xmltvByName[tvgName.replace(' ', '_')]
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
                            } catch (e: NumberFormatException) {
                                Sentry.captureException(e)
                                LOG.warn("Error parsing catchup days: {}, channel: {}", daysStr, m3uChannel.name)
                            }
                        }

                        IptvChannel(
                            name = m3uChannel.name,
                            externalPosition = ++externalIndex,
                            logo = logo,
                            groups = m3uChannel.groups,
                            epgId = tvgId,
                            catchupDays = days,
                            url = Url(m3uChannel.url).toEncodedJavaURI(),
                            server = server,
                            type = m3uChannel.url.toChannelType(),
                            m3uProps = m3uChannel.props,
                            vlcOpts = m3uChannel.vlcOpts,
                        )
                    })
                }

                if (account.isXtream()) {
                    xtreamRepository.signalXtreamImportStartedForServer(server.name)
                    // Update xtream info
                    // Load live streams
                    lateinit var liveStreamCategories: List<XtreamCategory>
                    lateinit var liveStreams: List<XtreamLiveStream>
                    server.withConnection(
                        config.timeouts.playlist.totalMilliseconds,
                        account,
                    ) { serverConnection, _ ->
                        liveStreamCategories = httpClient.get(
                            serverConnection.config.account!!.getXtreamLiveStreamCategoriesUrl().toString()
                        ) {
                            headers {
                                accept(ContentType.Application.Json)
                                addDefaultClientHeaders(serverConnection.config)
                            }
                        }.body()
                    }
                    server.withConnection(
                        config.timeouts.playlist.totalMilliseconds,
                        account,
                    ) { serverConnection, _ ->
                        liveStreams = httpClient.get(serverConnection.config.account!!.getXtreamLiveStreamsUrl().toString()) {
                            headers {
                                accept(ContentType.Application.Json)
                                addDefaultClientHeaders(serverConnection.config)
                            }
                        }.body()
                    }
                    xtreamRepository.upsertLiveStreamsAndCategories(liveStreams, liveStreamCategories, server.name)

                    // Load movies
                    lateinit var movieCategories: List<XtreamCategory>
                    lateinit var movies: List<XtreamMovie>
                    server.withConnection(
                        config.timeouts.playlist.totalMilliseconds,
                        account,
                    ) { serverConnection, _ ->
                        movieCategories = httpClient.get(serverConnection.config.account!!.getXtreamMovieCategoriesUrl().toString()) {
                            headers {
                                accept(ContentType.Application.Json)
                                addDefaultClientHeaders(serverConnection.config)
                            }
                        }.body<List<XtreamCategory>>()
                    }
                    server.withConnection(
                        config.timeouts.playlist.totalMilliseconds,
                        account,
                    ) { serverConnection, _ ->
                        movies = httpClient.get(serverConnection.config.account!!.getXtreamMoviesUrl().toString()) {
                            headers {
                                accept(ContentType.Application.Json)
                                addDefaultClientHeaders(serverConnection.config)
                            }
                        }.body<List<XtreamMovie>>()
                    }
                    xtreamRepository.upsertMoviesAndCategories(movies, movieCategories, server.name)

                    // Load tv series
                    lateinit var seriesCategories: List<XtreamCategory>
                    lateinit var series: List<XtreamSeries>
                    server.withConnection(
                        config.timeouts.playlist.totalMilliseconds,
                        account,
                    ) { serverConnection, _ ->
                        seriesCategories = httpClient.get(serverConnection.config.account!!.getXtreamSeriesCategoriesUrl().toString()) {
                            headers {
                                accept(ContentType.Application.Json)
                                addDefaultClientHeaders(serverConnection.config)
                            }
                        }.body<List<XtreamCategory>>()
                    }
                    server.withConnection(
                        config.timeouts.playlist.totalMilliseconds,
                        account,
                    ) { serverConnection, _ ->
                        series = httpClient.get(serverConnection.config.account!!.getXtreamSeriesUrl().toString()) {
                            headers {
                                accept(ContentType.Application.Json)
                                addDefaultClientHeaders(serverConnection.config)
                            }
                        }.body<List<XtreamSeries>>()
                    }
                    xtreamRepository.upsertSeriesAndCategories(series, seriesCategories, server.name)

                    xtreamRepository.signalXtreamImportCompletedForServer(server.name)
                }
            }

            channelRepository.upsertChannels(newChannels.values.toList())
            channelRepository.signalPlaylistImportCompletedForServer(server.name)
        }

        val missingChannelGroups = channelRepository.findChannelsWithMissingGroups()
        if (missingChannelGroups.isNotEmpty()) {
            val missingChannelGroups = missingChannelGroups.values.distinctBy { it.second }. map { group ->
                Pair(XtreamCategory(
                    id = "0",
                    name = group.second
                ), group.first)
            }

            xtreamRepository.upsertMissingChannelCategories(missingChannelGroups, type = IptvChannelType.live)
        }

        val channelCount = channelRepository.getIptvChannelCount()

        // Signal channels are updated
        if (channelCount > 0u) dispatchHook(HasOnApplicationEventHook::class, ChannelsAreAvailableEvent())

        LOG.info("{} channels available", channelCount)
    }

    private suspend fun loadXmltv(serverConnection: IptvServerConnection): InputStream {
        val response = httpClient.get(serverConnection.config.getEpgUrl().toString()) {
            headers {
                addDefaultClientHeaders(serverConnection.config)
            }
        }
        val inputStream = BufferedInputStream(response.bodyAsChannel().toInputStream())
        inputStream.mark(2)
        val peekInputStream = PeekableInputStream(inputStream)
        val data = peekInputStream.readNBytes(2)
        inputStream.reset()
        val isGzip = data.size >= 2 && data[0] == 0x1f.toByte() && data[1] == 0x8b.toByte()

        return if (isGzip) GZIPInputStream(inputStream) else inputStream
    }

    private suspend fun loadChannels(serverConnection: IptvServerConnection): InputStream {
        if (null == serverConnection.config.account) throw IllegalArgumentException("Cannot load channels without an account")

        @Suppress("UNNECESSARY_NOT_NULL_ASSERTION")
        val response = httpClient.get(serverConnection.config.account!!.getPlaylistUrl().toString()) {
            headers {
                addDefaultClientHeaders(serverConnection.config)
            }
        }
        return response.bodyAsChannel().toInputStream()
    }

    suspend fun getChannelPlaylist(
        channelId: UInt,
        user: IptvUser,
        baseUrl: URI,
        additionalHeaders: Headers = headersOf(),
        additionalQueryParameters: Parameters = parametersOf(),
        headersCallback: ((Headers) -> Unit)? = null,
    ): String {
        return (channelRepository.getChannelById(channelId)
        ?: run { throw RuntimeException("Channel not found") }).getPlaylist(user, baseUrl, additionalHeaders, additionalQueryParameters, headersCallback)
    }

    fun getAllChannelsPlaylist(
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

                if (!channel.server.config.proxyStream) channel.m3uProps.forEach { (key, value) ->
                    if (arrayOf("tvg-id", "tvg-name", "tvg-logo", "group-title", "catchup", "catchup-days").none { it == key }) {
                        outputWriter.write(" $key=\"$value\"")
                    }
                }

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
                    channel.groups.firstOrNull()?.let { outputWriter.write(it) }
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

                if (!channel.server.config.proxyStream && channel.vlcOpts.isNotEmpty()) {
                    channel.vlcOpts.forEach { (key, value) ->
                        outputWriter.write("#EXTVLCOPT:")
                        outputWriter.write("$key=$value")
                        outputWriter.write("\n")
                    }
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

    private fun scheduleChannelCleanups(delay: Long = 0, unit: TimeUnit = TimeUnit.MINUTES) {
        scheduledExecutorService.schedule(
            Thread {
                try {
                    CheckInUtils.withCheckIn("cleanup-channels", cleanupMonitorConfig) {
                        runBlocking {
                            databaseMutex.withLock {
                                channelRepository.cleanup()
                                epgRepository.cleanup()
                                xtreamRepository.cleanup()
                            }
                        }
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
                    CheckInUtils.withCheckIn("update-channels", updateMonitorConfig) {
                        runBlocking {
                            databaseMutex.withLock {
                                updateChannels()
                            }
                        }
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
        scheduleChannelCleanups(config.cleanupInterval.inWholeMinutes)
        LOG.info("Channel manager started")

        val channelCount = channelRepository.getIptvChannelCount()
        LOG.info("Channel count: $channelCount")
        if (channelCount > 0u) {
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
