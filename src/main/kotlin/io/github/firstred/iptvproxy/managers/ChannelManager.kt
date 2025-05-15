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
import io.github.firstred.iptvproxy.dtos.xmltv.XmltvChannel
import io.github.firstred.iptvproxy.dtos.xmltv.XmltvProgramme
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
import io.github.firstred.iptvproxy.parsers.XmltvParser
import io.github.firstred.iptvproxy.serialization.json
import io.github.firstred.iptvproxy.utils.addDefaultClientHeaders
import io.github.firstred.iptvproxy.utils.dispatchHook
import io.github.firstred.iptvproxy.utils.isHlsPlaylist
import io.github.firstred.iptvproxy.utils.toChannelType
import io.github.firstred.iptvproxy.utils.toEncodedJavaURI
import io.github.firstred.iptvproxy.utils.toProxiedIconUrl
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import io.ktor.utils.io.jvm.javaio.*
import io.sentry.MonitorConfig
import io.sentry.Sentry
import io.sentry.util.CheckInUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.io.Buffer
import kotlinx.io.asInputStream
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.decodeToSequence
import nl.adaptivity.xmlutil.ExperimentalXmlUtilApi
import nl.adaptivity.xmlutil.serialization.XmlParsingException
import org.apache.commons.io.input.buffer.PeekableInputStream
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.qualifier.named
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.URI
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
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

    @OptIn(ExperimentalSerializationApi::class, ExperimentalXmlUtilApi::class)
    private suspend fun updateChannels()
    {
        LOG.info("Updating channels")

        if (serversByName.isEmpty()) throw RuntimeException("No servers configured")

        serversByName.keys.forEach { channelRepository.signalPlaylistImportStartedForServer(it) }

        for (server in serversByName.values) {
            val newChannels: MutableList<IptvChannel> = mutableListOf()
            fun flushChannels() {
                channelRepository.upsertChannels(newChannels)
                newChannels.clear()
            }

            fun addNewChannel(channel: IptvChannel) {
                newChannels.add(channel)

                // Flush once db chunk size has been reached
                if (newChannels.count() >= config.database.chunkSize.toInt()) flushChannels()
            }

            if (null != server.config.epgUrl) {
                LOG.trace("Waiting for xmltv data to be downloaded")

                epgRepository.signalXmltvImportStartedForServer(server.name)

                server.withConnection(
                    config.timeouts.playlist.totalMilliseconds,
                    server.config.accounts?.first(),
                ) { serverConnection, _ ->
                    try {
                        LOG.trace("Parsing xmltv data")

                        val startOf = Clock.System.now() - server.config.epgBefore
                        val endOf = Clock.System.now() + server.config.epgAfter
                        val importStarted = Clock.System.now()

                        val xmltvChannels = mutableListOf<XmltvChannel>()
                        fun flushXmltvChannels() {
                            epgRepository.upsertXmltvChannels(xmltvChannels, importStarted)
                            xmltvChannels.clear()
                        }
                        fun addXmltvChannel(xmltvChannel: XmltvChannel) {
                            xmltvChannels.add(xmltvChannel)

                            // Flush once db chunk size has been reached
                            if (xmltvChannels.count() >= config.database.chunkSize.toInt()) flushXmltvChannels()
                        }

                        val xmltvProgrammes = mutableListOf<XmltvProgramme>()
                        fun flushXmltvProgrammes() {
                            epgRepository.upsertXmltvProgrammes(xmltvProgrammes)
                            xmltvProgrammes.clear()
                        }
                        fun addXmltvProgramme(xmltvProgramme: XmltvProgramme) {
                            if (xmltvProgramme.start > endOf || xmltvProgramme.stop < startOf) return

                            xmltvProgrammes.add(xmltvProgramme)

                            // Flush once db chunk size has been reached
                            if (xmltvProgrammes.count() >= config.database.chunkSize.toInt()) flushXmltvProgrammes()
                        }

                        loadXmltv(serverConnection).use { xmltvInputStream ->
                            XmltvParser.forEachXmltvItem(
                                xmltvInputStream,
                                onHeader = { xmltv ->
                                    epgRepository.upsertXmltvSourceForServer(xmltv, server.name)
                                },
                                onChannel = { addXmltvChannel(it) },
                                onProgramme = { addXmltvProgramme(it) },
                            )
                        }

                        flushXmltvChannels()
                        flushXmltvProgrammes()
                    } catch (e: XmlParsingException) {
                        LOG.warn("Unable to parse xmltv data: ${e.message} - skipping xmltv import for server ${server.name}")
                    }
                }

                epgRepository.signalXmltvImportCompletedForServer(server.name)
            }

            // All accounts should provide the same info, so we use the first one
            server.config.accounts?.firstOrNull()?.let { account ->
                LOG.trace("Parsing playlist: {}, url: {}", server.name, account.url)
                val liveCategoriesToRemapByName: Map<String, Pair<String, XtreamCategory>> = if (server.config.liveCategoryRemapping.isNotEmpty()) {
                    val newCategories = xtreamRepository.getAndCreateMissingCategoriesByName(server.config.liveCategoryRemapping.values.toList().distinct(), server.name)
                    server.config.liveCategoryRemapping.map { (key, value) ->
                        Pair(key, newCategories[value]!!)
                    }.associateBy({ it.first }, { it.second })
                } else {
                    mapOf()
                }
                val liveCategoriesToRemapByExternalId: Map<String, XtreamCategory> = liveCategoriesToRemapByName.values.associateBy({ it.first }, { it.second })

                server.withConnection(
                    config.timeouts.playlist.totalMilliseconds,
                    account,
                ) { serverConnection, _ ->
                    loadChannels(serverConnection).use { channelsInputStream ->

                        var externalIndex = 0u

                        try {
                            M3uParser.forEachChannel(channelsInputStream) { m3uChannel: M3uChannel ->
                                addNewChannel(run {
                                    val tvgName: String = m3uChannel.props["tvg-name"] ?: ""
                                    val tvgId: String =
                                        server.config.remapEpgChannelId(m3uChannel.props["tvg-id"] ?: "")

                                    var days = 0
                                    var daysStr = m3uChannel.props["tvg-rec"] ?: ""
                                    if (daysStr.isBlank()) daysStr = m3uChannel.props["catchup-days"] ?: ""
                                    if (daysStr.isNotBlank()) {
                                        try {
                                            days = daysStr.toInt()
                                        } catch (e: NumberFormatException) {
                                            Sentry.captureException(e)
                                            LOG.warn(
                                                "Error parsing catchup days: {}, channel: {}",
                                                daysStr,
                                                m3uChannel.name
                                            )
                                        }
                                    }

                                    val channelType = m3uChannel.url.toChannelType()
                                    IptvChannel(
                                        name = m3uChannel.name,
                                        externalPosition = ++externalIndex,
                                        logo = m3uChannel.props["tvg-logo"],
                                        groups = m3uChannel.groups.map {
                                            if (IptvChannelType.live == channelType) (liveCategoriesToRemapByName[it]?.second?.name
                                                ?: it) else it
                                        },
                                        epgId = tvgId,
                                        catchupDays = days,
                                        url = Url(m3uChannel.url).toEncodedJavaURI(),
                                        server = server,
                                        type = m3uChannel.url.toChannelType(),
                                        m3uProps = m3uChannel.props + mapOf(
                                            "tvg-id" to tvgId,
                                            "tvg-name" to tvgName,
                                        ) + mapOf(
                                            "group-title" to (liveCategoriesToRemapByName[m3uChannel.props["group-title"]]?.second?.name
                                                ?: m3uChannel.props["group-title"] ?: "")
                                        ).filterValues { it.isNotBlank() },
                                        vlcOpts = m3uChannel.vlcOpts,
                                    )
                                })
                            }
                        } catch (_: IOException) {
                            LOG.warn("Unable to parse m3u data: ${server.name} - skipping m3u import for server ${server.name}")
                        }
                        flushChannels()
                    }
                }

                if (account.isXtream()) {
                    xtreamRepository.signalXtreamImportStartedForServer(server.name)

                    // Load live streams
                    server.withConnection(
                        config.timeouts.playlist.totalMilliseconds,
                        account,
                    ) { serverConnection, _ ->
                        val buffer = Buffer()

                        httpClient.prepareGet(
                            serverConnection.config.account!!.getXtreamLiveStreamCategoriesUrl().toString()
                        ) {
                            headers {
                                accept(ContentType.Application.Json)
                                addDefaultClientHeaders(serverConnection.config)
                            }
                        }.execute { response ->
                            coroutineScope { async(Dispatchers.IO) {
                                response.bodyAsChannel().copyAndClose(buffer.asByteWriteChannel())
                            } }
                        }
                        val categories: MutableList<XtreamCategory> = mutableListOf()
                        json.decodeToSequence<XtreamCategory>(buffer.asInputStream()).forEach { category ->
                            categories.add(category)

                            if (categories.size > config.database.chunkSize.toInt()) {
                                xtreamRepository.upsertCategories(categories, server.name, IptvChannelType.live)
                                categories.clear()
                            }
                        }
                        xtreamRepository.upsertCategories(categories, server.name, IptvChannelType.live)
                    }

                    server.withConnection(
                        config.timeouts.playlist.totalMilliseconds,
                        account,
                    ) { serverConnection, _ ->
                        val buffer = Buffer()
                        httpClient.prepareGet(serverConnection.config.account!!.getXtreamLiveStreamsUrl().toString()) {
                            headers {
                                accept(ContentType.Application.Json)
                                addDefaultClientHeaders(serverConnection.config)
                            }
                        }.execute { response ->
                            coroutineScope { async(Dispatchers.IO) {
                                response.bodyAsChannel().copyAndClose(buffer.asByteWriteChannel())
                            } }
                        }

                        val liveStreams: MutableList<XtreamLiveStream> = mutableListOf()
                        json.decodeToSequence<XtreamLiveStream>(buffer.asInputStream()).forEach { liveStream ->
                            // Remap live stream categories if necessary
                            liveStreams.add(liveStream.copy(
                                categoryId = liveStream.categoryId?.let { liveCategoriesToRemapByExternalId[it] }?.id ?: liveStream.categoryId,
                                categoryIds = liveStream.categoryIds?.map { liveCategoriesToRemapByExternalId[it.toString()]?.id?.toUInt() ?: it },
                            ))

                            if (liveStreams.size > config.database.chunkSize.toInt()) {
                                xtreamRepository.upsertLiveStreams(liveStreams, server.name)
                                liveStreams.clear()
                            }
                        }
                        xtreamRepository.upsertLiveStreams(liveStreams, server.name)
                    }

                    // Load movies
                    server.withConnection(
                        config.timeouts.playlist.totalMilliseconds,
                        account,
                    ) { serverConnection, _ ->
                        val buffer = Buffer()
                        httpClient.prepareGet(serverConnection.config.account!!.getXtreamMovieCategoriesUrl().toString()) {
                            headers {
                                accept(ContentType.Application.Json)
                                addDefaultClientHeaders(serverConnection.config)
                            }
                        }.execute { response ->
                            coroutineScope { async(Dispatchers.IO) {
                                response.bodyAsChannel().copyAndClose(buffer.asByteWriteChannel())
                            } }
                        }

                        val categories: MutableList<XtreamCategory> = mutableListOf()
                        json.decodeToSequence<XtreamCategory>(buffer.asInputStream()).forEach { category ->
                            categories.add(category)

                            if (categories.size > config.database.chunkSize.toInt()) {
                                xtreamRepository.upsertCategories(categories, server.name, IptvChannelType.movie)
                                categories.clear()
                            }
                        }
                        xtreamRepository.upsertCategories(categories, server.name, IptvChannelType.movie)
                    }
                    server.withConnection(
                        config.timeouts.playlist.totalMilliseconds,
                        account,
                    ) { serverConnection, _ ->
                        val buffer = Buffer()
                        httpClient.prepareGet(serverConnection.config.account!!.getXtreamMoviesUrl().toString()) {
                            headers {
                                accept(ContentType.Application.Json)
                                addDefaultClientHeaders(serverConnection.config)
                            }
                        }.execute { response ->
                            coroutineScope { async(Dispatchers.IO) {
                                response.bodyAsChannel().copyAndClose(buffer.asByteWriteChannel())
                            } }
                        }

                        val movies: MutableList<XtreamMovie> = mutableListOf()
                        json.decodeToSequence<XtreamMovie>(buffer.asInputStream()).forEach { movie ->
                            movies.add(movie)

                            if (movies.size > config.database.chunkSize.toInt()) {
                                xtreamRepository.upsertMovies(movies, server.name)
                                movies.clear()
                            }
                        }
                        xtreamRepository.upsertMovies(movies, server.name)
                    }

                    // Load tv series
                    server.withConnection(
                        config.timeouts.playlist.totalMilliseconds,
                        account,
                    ) { serverConnection, _ ->
                        val buffer = Buffer()
                        httpClient.prepareGet(serverConnection.config.account!!.getXtreamSeriesCategoriesUrl().toString()) {
                            headers {
                                accept(ContentType.Application.Json)
                                addDefaultClientHeaders(serverConnection.config)
                            }
                        }.execute { response ->
                            coroutineScope { async(Dispatchers.IO) {
                                response.bodyAsChannel().copyAndClose(buffer.asByteWriteChannel())
                            } }
                        }

                        val categories: MutableList<XtreamCategory> = mutableListOf()
                        json.decodeToSequence<XtreamCategory>(buffer.asInputStream()).forEach { category ->
                            categories.add(category)

                            if (categories.size > config.database.chunkSize.toInt()) {
                                xtreamRepository.upsertCategories(categories, server.name, IptvChannelType.series)
                                categories.clear()
                            }
                        }
                        xtreamRepository.upsertCategories(categories, server.name, IptvChannelType.series)
                    }
                    server.withConnection(
                        config.timeouts.playlist.totalMilliseconds,
                        account,
                    ) { serverConnection, _ ->
                        val buffer = Buffer()
                        httpClient.prepareGet(serverConnection.config.account!!.getXtreamSeriesUrl().toString()) {
                            headers {
                                accept(ContentType.Application.Json)
                                addDefaultClientHeaders(serverConnection.config)
                            }
                        }.execute { response ->
                            coroutineScope { async(Dispatchers.IO) {
                                response.bodyAsChannel().copyAndClose(buffer.asByteWriteChannel())
                            } }
                        }

                        val seriesList: MutableList<XtreamSeries> = mutableListOf()
                        json.decodeToSequence<XtreamSeries>(buffer.asInputStream()).forEach { series ->
                            seriesList.add(series)

                            if (seriesList.size > config.database.chunkSize.toInt()) {
                                xtreamRepository.upsertSeries(seriesList, server.name)
                                seriesList.clear()
                            }
                        }
                        xtreamRepository.upsertSeries(seriesList, server.name)
                    }

                    xtreamRepository.signalXtreamImportCompletedForServer(server.name)
                }
            }

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
        return httpClient.prepareGet(serverConnection.config.getEpgUrl().toString()) {
            headers {
                addDefaultClientHeaders(serverConnection.config)
            }
        }.execute { response ->
            val buffer = Buffer()

            coroutineScope { async (Dispatchers.IO) {
                response.bodyAsChannel().copyAndClose(buffer.asByteWriteChannel())
            } }

            // Detect GZIP
            val inputStream = BufferedInputStream(buffer.asInputStream(), 32_000)
            inputStream.mark(2)
            val peekInputStream = PeekableInputStream(inputStream)
            val data = peekInputStream.readNBytes(2)
            inputStream.reset()
            val isGzip = data.size >= 2 && data[0] == 0x1f.toByte() && data[1] == 0x8b.toByte()

            if (isGzip) GZIPInputStream(inputStream) else inputStream
        }
    }

    private suspend fun loadChannels(serverConnection: IptvServerConnection): InputStream {
        if (null == serverConnection.config.account) throw IllegalArgumentException("Cannot load channels without an account")

        return httpClient.prepareGet(serverConnection.config.account.getPlaylistUrl().toString()) {
            headers {
                addDefaultClientHeaders(serverConnection.config)
            }
        }.execute { response ->
            val buffer = Buffer()

            coroutineScope { async (Dispatchers.IO) {
                response.bodyAsChannel().copyAndClose(buffer.asByteWriteChannel())
            } }

            buffer.asInputStream()
        }
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
        channelRepository.forEachIptvChannelChunk(forUser = user) { chunk ->
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
                    if (channel.url.isHlsPlaylist()) {
                        outputWriter.write(baseUrl.resolve("${channel.type.urlType()}/${user.username}/${user.password}/${channel.id}.m3u8").toString())
                    } else {
                        outputWriter.write(baseUrl.resolve("${channel.type.urlType()}/${user.username}/${user.password}/${channel.id}.${channel.url.toString().substringAfterLast('.').ifBlank { "m3u8" } }").toString())
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
                    LOG.warn("Scheduler interrupted while cleaning channels", e)
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
                        scheduleChannelCleanups()
                        scheduleChannelUpdates(config.updateInterval.inWholeMinutes)
                    }
                } catch (e: InterruptedException) {
                    LOG.warn("Scheduler interrupted while updating channels", e)
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
        LOG.trace("Channel manager starting")
        var nextUpdateRunInMinutes = 0L
        val channelCount = channelRepository.getIptvChannelCount()

        if (!config.updateOnStartup && channelCount > 0u) {
            val lastCompletedRun = channelRepository.findLastUpdateCompletedAt()
            // Find next run
            val now = Clock.System.now()
            val nextRun = (lastCompletedRun + config.updateInterval).coerceIn(now, now + config.updateInterval)
            nextUpdateRunInMinutes = nextRun.minus(now).inWholeMinutes
        }

        scheduleChannelUpdates(nextUpdateRunInMinutes)
        scheduleChannelCleanups(nextUpdateRunInMinutes + config.cleanupInterval.inWholeMinutes)
        LOG.trace("Channel manager started")

        LOG.info("Channel count: $channelCount")
        if (channelCount > 0u) {
            dispatchHook(HasOnApplicationEventHook::class, ChannelsAreAvailableEvent())
        }
    }

    override fun onApplicationTerminateHook() {
        LOG.trace("Channel manager stopping")

        try {
            scheduledExecutorService.shutdownNow()
            if (!scheduledExecutorService.awaitTermination(1, TimeUnit.MINUTES)) {
                LOG.warn("Scheduler is still running...")
                scheduledExecutorService.shutdownNow()
            }
        } catch (_: InterruptedException) {
            LOG.error("Interrupted while stopping scheduler")
        }

        LOG.trace("Channel manager stopped")
    }

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(ChannelManager::class.java)
    }
}
