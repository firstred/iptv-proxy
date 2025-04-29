package io.github.firstred.iptvproxy.managers

import io.github.firstred.iptvproxy.config
import io.github.firstred.iptvproxy.db.repositories.ChannelRepository
import io.github.firstred.iptvproxy.di.modules.IptvServersByName
import io.github.firstred.iptvproxy.dtos.m3u.M3uChannel
import io.github.firstred.iptvproxy.dtos.m3u.M3uDoc
import io.github.firstred.iptvproxy.dtos.xmltv.XmltvChannel
import io.github.firstred.iptvproxy.dtos.xmltv.XmltvDoc
import io.github.firstred.iptvproxy.dtos.xmltv.XmltvIcon
import io.github.firstred.iptvproxy.dtos.xmltv.XmltvProgramme
import io.github.firstred.iptvproxy.dtos.xmltv.XmltvUtils
import io.github.firstred.iptvproxy.entities.IptvChannel
import io.github.firstred.iptvproxy.entities.IptvServerConnection
import io.github.firstred.iptvproxy.entities.IptvUser
import io.github.firstred.iptvproxy.enums.IptvChannelType
import io.github.firstred.iptvproxy.events.ChannelsAreAvailableEvent
import io.github.firstred.iptvproxy.listeners.hooks.HasOnApplicationEventHook
import io.github.firstred.iptvproxy.listeners.hooks.lifecycle.HasApplicationOnDatabaseInitializedHook
import io.github.firstred.iptvproxy.listeners.hooks.lifecycle.HasApplicationOnStartHook
import io.github.firstred.iptvproxy.listeners.hooks.lifecycle.HasApplicationOnTerminateHook
import io.github.firstred.iptvproxy.parsers.M3uParser
import io.github.firstred.iptvproxy.utils.aesEncryptToHexString
import io.github.firstred.iptvproxy.utils.base64.encodeToBase64UrlString
import io.github.firstred.iptvproxy.utils.channelType
import io.github.firstred.iptvproxy.utils.dispatchHook
import io.github.firstred.iptvproxy.utils.hash
import io.ktor.client.*
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
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import kotlin.text.Charsets.UTF_8

class ChannelManager : KoinComponent, HasApplicationOnStartHook, HasApplicationOnTerminateHook,
    HasApplicationOnDatabaseInitializedHook {
    private val serversByName: IptvServersByName by inject()
    private val scheduledExecutorService: ScheduledExecutorService by inject()
    private val httpClient: HttpClient by inject()
    private val channelRepository: ChannelRepository by inject()

    private suspend fun updateChannels() {
        LOG.info("Updating channels")

        val newChannelsByReference: MutableMap<String, IptvChannel> = mutableMapOf()
        fun addNewChannel(channel: IptvChannel) {
            newChannelsByReference[channel.reference] = channel
        }

        if (serversByName.isEmpty()) throw RuntimeException("No servers configured")

        val newXmltv = XmltvDoc()
        for (server in serversByName.values) {
            var xmltv: XmltvDoc? = null
            if (server.config.epgUrl != null) {
                LOG.info("Waiting for xmltv data to be downloaded")

                server.withConnection(server.config.accounts?.first()) { serverConnection ->
                    LOG.info("Parsing xmltv data")
                    loadXmltv(serverConnection).let { inputStream ->
                        inputStream.use { xmltv = XmltvUtils.parseXmltv(it) }
                    }
                }
                if (null != xmltv) channelRepository.upsertXmltvDocForServer(xmltv, server.name)
            }

            val xmltvById: MutableMap<String, XmltvChannel> = mutableMapOf()
            val xmltvByName: MutableMap<String, XmltvChannel> = mutableMapOf()

            xmltv?.channels?.forEach { ch: XmltvChannel ->
                ch.id?.let { if (it.isNotBlank()) xmltvById[it] = ch }
                ch.displayNames?.forEach { textElem ->
                    textElem.text?.let {
                        if (it.isNotBlank()) xmltvByName[it] = ch
                    }
                }
            }

            val xmltvIds: MutableMap<String?, String?> = mutableMapOf()

            for (account in server.config.accounts ?: emptyList()) {
                LOG.info("Parsing playlist: {}, url: {}", server.name, account.url)

                lateinit var channelsInputStream: InputStream
                lateinit var m3u: M3uDoc

                server.withConnection(account) { serverConnection ->
                    channelsInputStream = loadChannels(serverConnection)
                    m3u = M3uParser.parse(channelsInputStream) ?: throw RuntimeException("Error parsing m3u")
                }

                m3u.channels.forEach { m3uChannel: M3uChannel ->
                    val channelReference = (server.name + "||" + m3uChannel.url).hash()

                    addNewChannel(newChannelsByReference[channelReference] ?: let {
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
                        val logo = (xmltvCh?.icon?.src ?: m3uChannel.props["tvg-logo"])?.let {
                            // Find basename and extension of image URL with regex
                            val regex = Regex("""^.+/(?<filename>((?<basename>[^.]*)\.(?<extension>.*)))$""")
                            val matchResult = regex.find(it)
                            val basename = URLEncoder.encode(
                                URLDecoder.decode(
                                    matchResult?.groups?.get("basename")?.value ?: "logo", UTF_8.toString()
                                ).filterNot { it.isWhitespace() }, UTF_8.toString()
                            )
                            val extension = URLEncoder.encode(
                                URLDecoder.decode(
                                    matchResult?.groups?.get("extension")?.value ?: "png", UTF_8.toString()
                                ).filterNot { it.isWhitespace() }, UTF_8.toString()
                            )

                            "\${BASE_URL}icon/\${ENCRYPTED_ACCOUNT}/${it.aesEncryptToHexString()}/${basename}.${extension}"
                        }

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

                        var xmltvId = xmltvCh?.id
                        if (!xmltvId.isNullOrBlank()) {
                            val newId = (server.name + '-' + xmltvId).hash()
                            if (xmltvIds.putIfAbsent(xmltvId, newId) == null) {
                                newXmltv.channels?.add(xmltvCh.copy(id = newId, icon = logo?.let { XmltvIcon(it) }))
                            }
                            xmltvId = newId
                        }

                        IptvChannel(
                            reference = channelReference,
                            name = m3uChannel.name,
                            logo = logo,
                            groups = m3uChannel.groups,
                            epgId = xmltvId,
                            catchupDays = days,
                            url = URI(m3uChannel.url),
                            server = server,
                            type = m3uChannel.url.channelType(),
                        )
                    })
                }
            }

            val endOf = Clock.System.now() + server.config.epgAfter
            val startOf = Clock.System.now() - server.config.epgBefore

            xmltv?.programmes?.forEach { programme: XmltvProgramme? ->
                if ((programme!!.start < endOf) && (programme.stop > startOf)
                ) {
                    val newId = xmltvIds[programme.channel]
                    if (!newId.isNullOrBlank()) newXmltv.programmes?.add(programme.copy(channel = newId))
                }
            }
        }

        val channelCount = channelRepository.getIptvChannelCount()

        if (channelCount > 0) dispatchHook(HasOnApplicationEventHook::class, ChannelsAreAvailableEvent())

        LOG.info("{} channels updated", channelRepository.getIptvChannelCount())
    }

    private fun buildNewLogoURI(it: String, extension: String, baseUrl: URI): URI? = URI(buildNewLogoURI(it, extension, baseUrl.toString()))
    private fun buildNewLogoURI(it: String, extension: String, baseUrl: String): String = "${baseUrl}icon/${it.encodeToBase64UrlString()}/logo." + when (extension) {
            "jpg"  -> "jpeg"
            "jpeg" -> "jpg"
            "gif"  -> "gif"
            "png"  -> "png"
            "webp" -> "webp"
            "avif" -> "avif"
            else   -> "png"
        }

    private suspend fun loadXmltv(serverConnection: IptvServerConnection): InputStream {
        val response = httpClient.get(serverConnection.config.getEpgUrl().toString()) {
            headers {
                serverConnection.config.account.userAgent?.let { append("User-Agent", it) }
            }
        }
        return response.bodyAsChannel().toInputStream()
    }

    private suspend fun loadChannels(serverConnection: IptvServerConnection): InputStream {
        val response = httpClient.get(serverConnection.config.account.getPlaylistUrl().toString()) {
            headers {
                serverConnection.config.account.userAgent?.let { append("User-Agent", it) }
            }
        }
        return response.bodyAsChannel().toInputStream()
    }

    suspend fun getChannelPlaylist(
        channelId: String,
        user: IptvUser,
        baseUrl: URI,
        additionalHeaders: Headers = headersOf(),
        additionalQueryParameters: Parameters = parametersOf(),
        headersCallback: ((Headers) -> Unit)? = null,
    ): String {
        TODO()
//        iptvChannelsLock.withLock {
//            return (channelsByReference[channelId] ?: throw RuntimeException("Channel not found: $channelId"))
//                .getPlaylist(user, baseUrl, additionalHeaders, additionalQueryParameters, headersCallback)
//        }
    }

    suspend fun getLiveStreamsPlaylist(
        outputStream: OutputStream,
        user: IptvUser,
        baseUrl: URI,
        filterType: IptvChannelType? = null,
    ) {
        TODO()
    }
    suspend fun getLiveStreamsPlaylist(
        user:
        IptvUser,
        actualBaseUrl:
        URI,
    ): String {
        val outputStream = ByteArrayOutputStream()
        getLiveStreamsPlaylist(outputStream, user, actualBaseUrl)
        return outputStream.toString("UTF-8")
    }

    private fun scheduleChannelCleanups(delay: Long =0, unit: TimeUnit = TimeUnit.MINUTES) {
        scheduledExecutorService.schedule(
            Thread {
                try {
                    runBlocking {
                        channelRepository.cleanup()
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

    override fun onApplicationStartHook() {
        LOG.info("Channel manager starting")

        scheduleChannelUpdates()
        scheduleChannelCleanups()

        LOG.info("Channel manager started")
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

    override fun onApplicationDatabaseInitializedHook() {
        val channelCount = channelRepository.getIptvChannelCount()
        LOG.info("Channel count: $channelCount")
        if (channelCount > 0) {
            dispatchHook(HasOnApplicationEventHook::class, ChannelsAreAvailableEvent())
        }
    }

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(ChannelManager::class.java)
    }
}
