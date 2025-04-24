package io.github.firstred.iptvproxy.managers

import io.github.firstred.iptvproxy.config
import io.github.firstred.iptvproxy.di.modules.IptvChannelsByReference
import io.github.firstred.iptvproxy.di.modules.IptvServersByName
import io.github.firstred.iptvproxy.dtos.m3u.M3uChannel
import io.github.firstred.iptvproxy.dtos.xmltv.XmltvChannel
import io.github.firstred.iptvproxy.dtos.xmltv.XmltvDoc
import io.github.firstred.iptvproxy.dtos.xmltv.XmltvIcon
import io.github.firstred.iptvproxy.dtos.xmltv.XmltvProgramme
import io.github.firstred.iptvproxy.dtos.xmltv.XmltvUtils
import io.github.firstred.iptvproxy.entities.IptvChannel
import io.github.firstred.iptvproxy.entities.IptvServer
import io.github.firstred.iptvproxy.entities.IptvUser
import io.github.firstred.iptvproxy.events.ChannelsUpdatedEvent
import io.github.firstred.iptvproxy.listeners.hooks.HasOnApplicationEventHook
import io.github.firstred.iptvproxy.listeners.hooks.lifecycle.HasApplicationOnStartHook
import io.github.firstred.iptvproxy.listeners.hooks.lifecycle.HasApplicationOnTerminateHook
import io.github.firstred.iptvproxy.parsers.M3uParser
import io.github.firstred.iptvproxy.utils.aesEncryptToHexString
import io.github.firstred.iptvproxy.utils.base64.encodeToBase64UrlString
import io.github.firstred.iptvproxy.utils.dispatchHook
import io.github.firstred.iptvproxy.utils.hash
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.jvm.javaio.toInputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import org.apache.commons.text.StringSubstitutor
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

class ChannelManager : KoinComponent, HasApplicationOnStartHook, HasApplicationOnTerminateHook {
    private val serversByName: IptvServersByName by inject()
    private val channelsByReference: IptvChannelsByReference by inject()
    private val scheduledExecutorService: ScheduledExecutorService by inject()
    private val httpClient: HttpClient by inject()
    private val coroutineScope = CoroutineScope(Job())

    private suspend fun updateChannels() {
        LOG.info("Updating channels")

        val newChannelsByReference = IptvChannelsByReference()
        fun addNewChannel(channel: IptvChannel) {
            newChannelsByReference[channel.reference] = channel
        }

        val loads: MutableMap<IptvServer, InputStream> = mutableMapOf()
        val xmltvLoads: MutableMap<IptvServer, InputStream> = mutableMapOf()

        if (serversByName.isEmpty()) throw RuntimeException("No servers configured")

        serversByName.values.forEach { server: IptvServer ->
            if (server.config.epgUrl != null) xmltvLoads[server] = loadXmltv(server)
            loads[server] = loadChannels(server)
        }

        val newXmltv = XmltvDoc()
        for (server in serversByName.values) {
            var xmltv: XmltvDoc? = null
            if (server.config.epgUrl != null) {
                LOG.info("Waiting for xmltv data to be downloaded")

                xmltvLoads[server]?.let { inputStream ->
                    LOG.info("Parsing xmltv data")
                    inputStream.use { xmltv = XmltvUtils.parseXmltv(it) }
                }
            }

            val xmltvById: MutableMap<String?, XmltvChannel?> = mutableMapOf()
            val xmltvByName: MutableMap<String?, XmltvChannel?> = mutableMapOf()

            xmltv?.channels?.forEach { ch: XmltvChannel ->
                xmltvById[ch.id] = ch
                ch.displayNames?.forEach { xmltvByName[it.text] = ch }
            }

            val xmltvIds: MutableMap<String?, String?> = mutableMapOf()

            for (account in server.config.accounts ?: emptyList()) {
                LOG.info("Parsing playlist: {}, url: {}", server.name, account.url)

                val channelsInputStream = (loads[server] ?: throw RuntimeException("No channels loaded for server: " + server.name))
                val m3u = M3uParser.parse(channelsInputStream) ?: throw RuntimeException("Error parsing m3u")

                m3u.channels.forEach { m3uChannel: M3uChannel? ->
                    if (m3uChannel == null) return@forEach

                    val channelReference = (server.name + "||" + m3uChannel.name).hash()

                    addNewChannel(newChannelsByReference[channelReference] ?: let {
                        val tvgId = m3uChannel.props["tvg-id"]
                        val tvgName = m3uChannel.props["tvg-name"]

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
                        if (tvgId != null) {
                            xmltvCh = xmltvById[tvgId]
                        }
                        if (xmltvCh == null && tvgName != null) {
                            xmltvCh = xmltvByName[tvgName]
                            if (xmltvCh == null) {
                                xmltvCh = xmltvByName[tvgName.replace(' ', '_')]
                            }
                        }
                        if (xmltvCh == null) {
                            xmltvCh = xmltvByName[m3uChannel.name]
                        }

                        var logo = m3uChannel.props["tvg-logo"]
                        xmltvCh?.icon?.src?.let { logo = it }

                        // Redirect logo URI
                        logo?.let {
                            // Find basename and extension of image URL with regex
                            val regex = Regex("""^.+/(?<filename>((?<basename>[^.]*)\.(?<extension>.*)))$""")
                            val matchResult = regex.find(it)
                            val basename = URLEncoder.encode(URLDecoder.decode(matchResult?.groups?.get("basename")?.value ?: "logo", UTF_8.toString()).filterNot { it.isWhitespace() }, UTF_8.toString())
                            val extension = URLEncoder.encode(URLDecoder.decode(matchResult?.groups?.get("extension")?.value ?: "png", UTF_8.toString()).filterNot { it.isWhitespace() }, UTF_8.toString())

                            logo = "\${BASE_URL}icon/\${ENCRYPTED_ACCOUNT}/${it.aesEncryptToHexString()}/${basename}.${extension}"
                        }

                        var days = 0
                        var daysStr = m3uChannel.props["tvg-rec"]
                        if (daysStr == null) {
                            daysStr = m3uChannel.props["catchup-days"]
                        }
                        if (daysStr != null) {
                            try {
                                days = daysStr.toInt()
                            } catch (_: NumberFormatException) {
                                LOG.warn("Error parsing catchup days: {}, channel: {}", daysStr, m3uChannel.name)
                            }
                        }

                        var xmltvId = xmltvCh?.id
                        if (xmltvId != null) {
                            val newId = (server.name + '-' + xmltvId).hash()
                            if (xmltvIds.putIfAbsent(xmltvId, newId) == null) {
                                newXmltv.channels?.add(xmltvCh.copy(id = newId, icon = logo?.let { XmltvIcon(it) }))
                            }
                            xmltvId = newId
                        }

                        IptvChannel(
                            reference = channelReference,
                            name = m3uChannel.name,
                            logo = logo.toString(),
                            groups = m3uChannel.groups,
                            xmltvId = xmltvId.toString(),
                            catchupDays = days,
                            url = URI.create(m3uChannel.url),
                            server = server,
                        )
                    })
                }
            }

            val endOf = if (server.config.epgAfter == null) null else Clock.System.now() + server.config.epgAfter
            val startOf = if (server.config.epgBefore == null) null else Clock.System.now() - server.config.epgBefore

            xmltv?.programmes?.forEach { programme: XmltvProgramme? ->
                if ((endOf == null || programme!!.start!! < endOf) && (startOf == null || programme!!.stop!! > startOf)
                ) {
                    val newId = xmltvIds[programme!!.channel]
                    if (newId != null) {
                        newXmltv.programmes?.add(programme.copy(channel = newId))
                    }
                }
            }
        }

        // Write new XMLTV file
        XmltvUtils.writeXmltv(newXmltv)

        // Update channels list
        channelsByReference.clear()
        channelsByReference.putAll(newChannelsByReference)

        dispatchHook(HasOnApplicationEventHook::class, ChannelsUpdatedEvent())

        LOG.info("{} channels updated", channelsByReference.size)
    }

    private fun buildNewLogoURI(it: String, extension: String, baseUrl: URI): URI? = URI.create(buildNewLogoURI(it, extension, baseUrl.toString()))
    private fun buildNewLogoURI(it: String, extension: String, baseUrl: String): String = "${baseUrl}icon/${it.encodeToBase64UrlString()}/logo." + when (extension) {
            "jpg"  -> "jpeg"
            "jpeg" -> "jpg"
            "gif"  -> "gif"
            "png"  -> "png"
            "webp" -> "webp"
            "avif" -> "avif"
            else   -> "png"
        }

    private suspend fun loadXmltv(server: IptvServer): InputStream {
        val response = httpClient.get(server.config.epgUrl.toString())
        return response.bodyAsChannel().toInputStream()
    }

    private suspend fun loadChannels(server: IptvServer): InputStream {
        val response = httpClient.get(server.config.accounts!!.first().url!!)
        return response.bodyAsChannel().toInputStream()
    }

    suspend fun getChannelPlaylist(channelId: String, user: IptvUser, baseUrl: URI): String
    {
        return (channelsByReference[channelId] ?: throw RuntimeException("Channel not found: $channelId"))
            .getPlaylist(user, baseUrl)
    }

    suspend fun getAllChannelsPlaylist(outputStream: OutputStream, user: IptvUser, baseUrl: URI) {
        val outputWriter = outputStream.bufferedWriter(UTF_8)
        val sortedChannels = channelsByReference.values.let {
            if (config.sortChannels) it.sortedBy { obj: IptvChannel -> obj.name }
            else it
        }

        // Replace the env variable placeholders in the config
        val substitutor = StringSubstitutor(mapOf(
            "BASE_URL" to baseUrl.toString(),
            "ENCRYPTED_ACCOUNT" to user.toEncryptedAccountHexString(),
        ))

        outputWriter.write("#EXTM3U\n")

        sortedChannels.forEach { channel: IptvChannel ->
            outputWriter.write("#EXTINF:-1")
            if (channel.xmltvId.isNotEmpty()) {
                outputWriter.write(" tvg-id=\"")
                outputWriter.write(channel.xmltvId)
                outputWriter.write("\"")
            }

            if (channel.logo.isNotEmpty()) {
                outputWriter.write(" tvg-logo=\"")
                outputWriter.write(substitutor.replace(channel.logo))
                outputWriter.write("\"")
            }

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

            outputWriter.write(baseUrl.toString())
            outputWriter.write("live/")
            outputWriter.write(("${user.username}_${user.password}".aesEncryptToHexString() + "/"))
            outputWriter.write(channel.reference)
            outputWriter.write("/channel.m3u8")
            outputWriter.write("\n")

            outputWriter.flush()
        }
    }
    suspend fun getAllChannelsPlaylist(user: IptvUser, actualBaseUrl: URI): String {
        val outputStream = ByteArrayOutputStream()
        getAllChannelsPlaylist(outputStream, user, actualBaseUrl)
        return outputStream.toString("UTF-8")
    }

    override fun onApplicationStartHook() {
        LOG.info("Scheduler starting")

         coroutineScope.launch { scheduleUpdateChannels() }

        LOG.info("Scheduler started")
    }

    override fun onApplicationTerminateHook() {
        LOG.info("Scheduler stopping")

        try {
            scheduledExecutorService.shutdownNow()
            if (!scheduledExecutorService.awaitTermination(1, TimeUnit.MINUTES)) {
                LOG.warn("Scheduler is still running...")
                scheduledExecutorService.shutdownNow()
            }
        } catch (e: InterruptedException) {
            LOG.error("Interrupted while stopping scheduler")
        }

        LOG.info("Scheduler stopped")
    }

    private suspend fun scheduleUpdateChannels(delay: Long = 0, unit: TimeUnit = TimeUnit.MINUTES) {
        scheduledExecutorService.schedule(
            Thread {
                try {
                    runBlocking(coroutineScope.coroutineContext) {
                        updateChannels()
                        scheduleUpdateChannels(config.updateInterval.inWholeMinutes)
                    }
                } catch (e: InterruptedException) {
                    LOG.info("Scheduler interrupted while updating channels", e)
                } catch (e: Exception) {
                    LOG.error("Error while updating channels", e)
                    runBlocking(coroutineScope.coroutineContext) {
                        scheduleUpdateChannels(1)
                    }
                }
            },
            delay,
            unit,
        )
    }

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(ChannelManager::class.java)
    }
}
