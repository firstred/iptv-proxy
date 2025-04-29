package io.github.firstred.iptvproxy.routes

import io.github.firstred.iptvproxy.config
import io.github.firstred.iptvproxy.db.repositories.ChannelRepository
import io.github.firstred.iptvproxy.dtos.xmltv.XmltvChannel
import io.github.firstred.iptvproxy.dtos.xmltv.XmltvProgramme
import io.github.firstred.iptvproxy.entities.IptvUser
import io.github.firstred.iptvproxy.enums.IptvChannelType
import io.github.firstred.iptvproxy.managers.ChannelManager
import io.github.firstred.iptvproxy.plugins.findUserFromXtreamAccountInRoutingContext
import io.github.firstred.iptvproxy.plugins.isNotMainEndpoint
import io.github.firstred.iptvproxy.plugins.isNotReady
import io.github.firstred.iptvproxy.serialization.xml
import io.github.firstred.iptvproxy.utils.toProxiedIconUrl
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

fun Route.xtreamApi() {
    val channelManager: ChannelManager by inject()
    val channelRepository: ChannelRepository by inject()

    get("/xmltv.php") {
        if (isNotMainEndpoint()) return@get
        if (isNotReady()) return@get

        lateinit var user: IptvUser
        try {
            user = findUserFromXtreamAccountInRoutingContext()
        } catch (_: Throwable) {
            call.respond(HttpStatusCode.Unauthorized, "Username and/or password incorrect")
            return@get
        }

        call.response.headers.apply {
            append(HttpHeaders.ContentType, ContentType.Application.Xml.toString())
            append(HttpHeaders.ContentDisposition, "attachment; filename=xmltv.xml")
        }

        val baseUrl = config.getActualBaseUrl(call.request).toString()
        val encryptedAccount = user.toEncryptedAccountHexString()
        call.respondTextWriter {
            write("<?xml version=\"1.0\" encoding=\"UTF-8\"?><tv generator-info-name=\"iptv-proxy\">")
            for (server in config.servers.map { it.name }) {
                channelRepository.forEachEpgChannelChunk(server) {
                    it.forEach { row ->
                        write(
                            xml.encodeToString(
                                XmltvChannel.serializer(), row.copy(
                                    icon = row.icon?.copy(src = row.icon.src?.toProxiedIconUrl(baseUrl, encryptedAccount)),
                                )
                            )
                        )
                        flush()
                    }
                }
            }
            for (server in config.servers.map { it.name }) {
                channelRepository.forEachEpgProgrammeChunk(server) {
                    it.forEach { row ->
                        write(xml.encodeToString(XmltvProgramme.serializer(), row))
                        flush()
                    }
                }
            }

            write("</tv>")
            flush()
        }
    }

    get(Regex("""/(player_api|panel_api).php""")) {
        if (isNotMainEndpoint()) return@get
        if (isNotReady()) return@get

        lateinit var user: IptvUser
        try {
            user = findUserFromXtreamAccountInRoutingContext()
        } catch (_: Throwable) {
            call.respond(HttpStatusCode.Unauthorized, "Username and/or password incorrect")
            return@get
        }

        call.response.headers.apply {
            append(HttpHeaders.ContentType, "audio/mpegurl")
            append(HttpHeaders.ContentDisposition, "attachment; filename=playlist.m3u8")
        }

        when {
            call.request.queryParameters["action"] == "get_live_streams" -> {
                call.respondOutputStream { use { output ->
                    channelManager.getLiveStreamsPlaylist(
                        output,
                        user,
                        config.getActualBaseUrl(call.request),
                        IptvChannelType.LIVE,
                    )
                    output.flush()
                } }
            }

            call.request.queryParameters["action"] == "get_series" -> {
                call.respondOutputStream { use { output ->
                    channelManager.getLiveStreamsPlaylist(
                        output,
                        user,
                        config.getActualBaseUrl(call.request),
                        IptvChannelType.SERIES,
                    )
                    output.flush()
                } }
            }

            listOf("get_vod_streams", "get_movies_streams").contains(call.request.queryParameters["action"]) -> {
                call.respondOutputStream { use { output ->
                    channelManager.getLiveStreamsPlaylist(
                        output,
                        user,
                        config.getActualBaseUrl(call.request),
                        IptvChannelType.MOVIE,
                    )
                    output.flush()
                } }
            }

            else -> {
                call.respond(HttpStatusCode.BadRequest, "Invalid action")
            }
        }
    }

    get("/get.php") {
        if (isNotMainEndpoint()) return@get
        if (isNotReady()) return@get

        lateinit var user: IptvUser
        try {
            user = findUserFromXtreamAccountInRoutingContext()
        } catch (_: Throwable) {
            call.respond(HttpStatusCode.Unauthorized, "Username and/or password incorrect")
            return@get
        }

        call.response.headers.apply {
            append(HttpHeaders.ContentType, "audio/mpegurl")
            append(HttpHeaders.ContentDisposition, "attachment; filename=playlist.m3u8")
        }

        call.respondOutputStream { use { output ->
            channelManager.getLiveStreamsPlaylist(
                output,
                user,
                config.getActualBaseUrl(call.request),
            )
            output.flush()
        } }
    }
}
