package io.github.firstred.iptvproxy.managers

import java.net.URLDecoder
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
import io.github.firstred.iptvproxy.events.ChannelsUpdatedEvent
import io.github.firstred.iptvproxy.listeners.HasOnApplicationEventHook
import io.github.firstred.iptvproxy.parsers.M3uParser
import io.github.firstred.iptvproxy.utils.base64.encodeToBase64UrlString
import io.github.firstred.iptvproxy.utils.dispatchHook
import io.github.firstred.iptvproxy.utils.generateUserToken
import io.github.firstred.iptvproxy.utils.hash
import io.github.firstred.iptvproxy.utils.pathSignature
import kotlinx.datetime.Clock
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.URI
import java.net.URISyntaxException
import java.util.regex.Pattern
import kotlin.text.Charsets.UTF_8

class ChannelManager : KoinComponent {
    private val serversByName: IptvServersByName by inject()
    private val channelsByReference: IptvChannelsByReference by inject()

    fun updateChannels() {
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

                        if (logo == null && xmltvCh != null && xmltvCh.icon != null && xmltvCh.icon!!.src != null) {
                            xmltvCh.icon?.src?.let { logo = it }
                        }

                        // Redirect logo URI
                        logo?.let {
                            // Find basename and extension of image URL with regex
                            val regex = Regex("""^.+/(?<filename>((?<basename>[^.]*)\.(?<extension>.*)))$""")
                            val matchResult = regex.find(it)
                            val basename = URLDecoder.decode(matchResult?.groups?.get("basename")?.value ?: "logo", UTF_8.toString()).filterNot { it.isWhitespace() }
                            val extension = URLDecoder.decode(matchResult?.groups?.get("extension")?.value ?: "png", UTF_8.toString()).filterNot { it.isWhitespace() }

                            val uri = try {
                                URI.create("${config.baseUrl}/icon/${it.encodeToBase64UrlString()}/${basename}.${extension}")
                            } catch (e: URISyntaxException) {
                                buildNewLogoURI(it, extension)
                            } catch (e: IllegalArgumentException) {
                                buildNewLogoURI(it, extension)
                            } ?: return

                            val pathSignature = uri.path.pathSignature()

                            logo = "${config.baseUrl}/${uri.path.substringBeforeLast('/')}/$pathSignature/${uri.path.substringAfterLast('/')}"
                        }

                        var days = 0
                        var daysStr = m3uChannel.props["tvg-rec"]
                        if (daysStr == null) {
                            daysStr = m3uChannel.props["catchup-days"]
                        }
                        if (daysStr != null) {
                            try {
                                days = daysStr.toInt()
                            } catch (e: NumberFormatException) {
                                LOG.warn("Error parsing catchup days: {}, channel: {}", daysStr, m3uChannel.name)
                            }
                        }

                        var xmltvId = xmltvCh?.id
                        if (xmltvId != null) {
                            val newId = (server.name + '-' + xmltvId).hash()
                            if (xmltvIds.putIfAbsent(xmltvId, newId) == null) {
                                newXmltv.channels?.add(xmltvCh!!.copy(id = newId, icon = logo?.let { XmltvIcon(it) }))
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

    private fun buildNewLogoURI(it: String, extension: String): URI? = URI.create(
        "${config.baseUrl}/icon/${it.encodeToBase64UrlString()}/logo." + when (extension) {
            "jpg"  -> "jpeg"
            "jpeg" -> "jpg"
            "gif"  -> "gif"
            "png"  -> "png"
            "webp" -> "webp"
            "avif" -> "avif"
            else   -> "png"
        }
    )

    private fun loadXmltv(server: IptvServer): InputStream {
        return File("epg_orig.xml").inputStream()

//        val f = FileLoader.tryLoadBytes(sg.xmltvUrl!!)
//        return FileLoader.tryLoadBytes("file://epg_orig.xml") ?: run { throw RuntimeException("File not found") }
//        return f ?: xmltvLoader.loadAsync("xmltv: " + sg.name, sg.xmltvUrl, defaultHttpClient)
    }

    private fun loadChannels(server: IptvServer): InputStream {
        return File("playlist_orig.m3u8").inputStream()

//        val f = FileLoader.tryLoadString(s.url.toString())
//
//         Local playlist_orig.m3u8 file to CompletableFuture<String?>
//        return FileLoader.tryLoadString("playlist_orig.m3u8") ?: run { throw RuntimeException("File not found") }

//        return f ?: channelsLoader.loadAsync(
//            "playlist: " + s.name,
//            s.createRequest(s.url.toString()).build(),
//            s.httpClient,
//        )
    }

    fun getChannelPlaylist(channelId: String): String
    {
        TODO()

//        return (channelsByReference[channelId] ?: throw RuntimeException("Channel not found: $channelId")).getPlaylist()
    }

    fun getAllChannelsPlaylist(outputStream: OutputStream, username: String) {
        val sortedChannels = channelsByReference.values.let {
            if (config.sortChannels) it.sortedBy { obj: IptvChannel -> obj.name }
            else it
        }

        outputStream.write("#EXTM3U\n".toByteArray())

        sortedChannels.forEach { channel: IptvChannel ->
            outputStream.write("#EXTINF:-1".toByteArray())
            if (channel.xmltvId.isNotEmpty()) {
                outputStream.write(" tvg-id=\"".toByteArray())
                outputStream.write(channel.xmltvId.toByteArray())
                outputStream.write("\"".toByteArray())
            }

            if (channel.logo.isNotEmpty()) {
                outputStream.write(" tvg-logo=\"".toByteArray())
                outputStream.write(channel.logo.toByteArray())
                outputStream.write("\"".toByteArray())
            }

            if (channel.catchupDays > 0) {
                outputStream.write(" catchup=\"shift\" catchup-days=\"".toByteArray())
                outputStream.write(channel.catchupDays.toString().toByteArray())
                outputStream.write("\"".toByteArray())
            }

            if (channel.groups.isNotEmpty()) {
                outputStream.write(" group-title=\"".toByteArray())
                outputStream.write(channel.groups.first().toByteArray())
                outputStream.write("\"".toByteArray())
            }

            outputStream.write(",".toByteArray())
            outputStream.write(channel.name.toByteArray())
            outputStream.write("\n".toByteArray())

            if (channel.groups.isNotEmpty()) {
                outputStream.write("#EXTGRP:".toByteArray())
                outputStream.write(java.lang.String.join(";", channel.groups).toByteArray())
                outputStream.write("\n".toByteArray())
            }

            outputStream.write(config.baseUrl.toByteArray())
            outputStream.write("/live/".toByteArray())
            outputStream.write("${username}_${username.generateUserToken()}/".toByteArray())
            outputStream.write(channel.reference.toByteArray())
            outputStream.write("/channel.m3u8".toByteArray())
            outputStream.write("\n".toByteArray())

            outputStream.flush()
        }
    }
    fun getAllChannelsPlaylist(username: String): String {
        val outputStream = ByteArrayOutputStream()
        getAllChannelsPlaylist(outputStream, username)
        return outputStream.toString("UTF-8")
    }

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(ChannelManager::class.java)
    }
}
