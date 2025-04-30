package io.github.firstred.iptvproxy.routes

import io.github.firstred.iptvproxy.config
import io.github.firstred.iptvproxy.db.repositories.EpgRepository
import io.github.firstred.iptvproxy.db.repositories.XtreamRepository
import io.github.firstred.iptvproxy.dtos.xmltv.XmltvChannel
import io.github.firstred.iptvproxy.dtos.xmltv.XmltvProgramme
import io.github.firstred.iptvproxy.dtos.xtream.XtreamInfo
import io.github.firstred.iptvproxy.dtos.xtream.XtreamLiveStream
import io.github.firstred.iptvproxy.dtos.xtream.XtreamServerInfo
import io.github.firstred.iptvproxy.dtos.xtream.XtreamUserInfo
import io.github.firstred.iptvproxy.entities.IptvUser
import io.github.firstred.iptvproxy.enums.XtreamOutputFormat
import io.github.firstred.iptvproxy.managers.ChannelManager
import io.github.firstred.iptvproxy.plugins.findUserFromXtreamAccountInRoutingContext
import io.github.firstred.iptvproxy.plugins.isNotMainPort
import io.github.firstred.iptvproxy.plugins.isNotReady
import io.github.firstred.iptvproxy.serialization.json
import io.github.firstred.iptvproxy.serialization.xml
import io.github.firstred.iptvproxy.utils.toProxiedIconUrl
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.datetime.Clock
import kotlinx.datetime.format
import kotlinx.datetime.format.DateTimeComponents
import kotlinx.datetime.format.FormatStringsInDatetimeFormats
import kotlinx.datetime.format.byUnicodePattern
import org.koin.ktor.ext.inject
import kotlin.time.Duration

@OptIn(FormatStringsInDatetimeFormats::class)
fun Route.xtreamApi() {
    val channelManager: ChannelManager by inject()
    val epgRepository: EpgRepository by inject()
    val xtreamRepository: XtreamRepository by inject()

    get("/xmltv.php") {
        if (isNotMainPort()) return@get
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
                epgRepository.forEachEpgChannelChunk(server) {
                    it.forEach { row ->
                        write(
                            xml.encodeToString(
                                XmltvChannel.serializer(), row.copy(
                                    icon = row.icon?.copy(
                                        src = row.icon.src?.toProxiedIconUrl(
                                            baseUrl,
                                            encryptedAccount
                                        )
                                    ),
                                )
                            )
                        )
                        flush()
                    }
                }
            }
            for (server in config.servers.map { it.name }) {
                epgRepository.forEachEpgProgrammeChunk(server) {
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

    get("/player_api.php") {
        if (isNotMainPort()) return@get
        if (isNotReady()) return@get

        lateinit var user: IptvUser
        try {
            user = findUserFromXtreamAccountInRoutingContext()
        } catch (_: Throwable) {
            call.respond(HttpStatusCode.Unauthorized, "Username and/or password incorrect")
            return@get
        }

        val baseUrl = config.getActualBaseUrl(call.request)

        when {
            call.request.queryParameters["action"] == "get_live_streams" -> {
                call.response.headers.apply {
                    append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                }


                call.respondTextWriter {
                    write("[")
                    var first = true
                    xtreamRepository.forEachLiveStreamChunk { list ->
                        list.forEachIndexed { idx, it ->
                            if (!first) write(",")
                            else first = false

                            write(json.encodeToString(XtreamLiveStream.serializer(), it))
                            flush()
                        }
                    }

                    write("]")
                    flush()
                }
            }

            call.request.queryParameters["action"] == "get_series" -> {
                call.respondText("Not implemented", ContentType.Text.Plain, HttpStatusCode.NotImplemented)
            }

            call.request.queryParameters["action"] == "get_series_info" -> {
                call.respondText("[]", ContentType.Application.Json, HttpStatusCode.OK)
            }

            listOf("get_vod_streams", "get_movies_streams").contains(call.request.queryParameters["action"]) -> {
                call.respondText("Not implemented", ContentType.Text.Plain, HttpStatusCode.NotImplemented)
            }

            else -> {
                call.respond(
                    XtreamInfo(
                        XtreamUserInfo(
                            username = user.username,
                            password = user.password,
                            message = "",
                            auth = 1,
                            status = "Active",
                            expirationDate = (Clock.System.now() + Duration.parse("P365D")).epochSeconds.toString(),
                            isTrial = "0",
                            createdAt = (Clock.System.now() - Duration.parse("P365D")).epochSeconds.toString(),
                            activeConnections = "0",
                            maxConnections = user.maxConnections.toString(),
                            allowedOutputFormats = listOf(XtreamOutputFormat.hls),
                        ),
                        XtreamServerInfo(
                            timezone = "UTC",
                            timeNow = Clock.System.now().format(DateTimeComponents.Format {
                                byUnicodePattern("yyyy-MM-dd HH:mm:ss")
                            }),
                            timestampNow = (Clock.System.now() + Duration.parse("P365D")).epochSeconds.toString(),
                            process = true,
                            url = baseUrl.host,
                            port = baseUrl.port.toString(),
                            protocol = baseUrl.scheme,
                            httpsPort = if ("https" == baseUrl.scheme) "443" else "",
                            rtmpPort = "",
                        ),
                    )
                )
            }
        }
    }

    get("/panel_api.php") {
        if (isNotMainPort()) return@get
        if (isNotReady()) return@get

        lateinit var user: IptvUser
        try {
            user = findUserFromXtreamAccountInRoutingContext()
        } catch (_: Throwable) {
            call.respond(HttpStatusCode.Unauthorized, "Username and/or password incorrect")
            return@get
        }

        when {
            // Get EPG
            call.request.queryParameters["action"] == "get_epg" -> {
                call.respondText("Not implemented", ContentType.Text.Plain, HttpStatusCode.NotImplemented)
            }

            else -> {
                call.respondText("Not implemented", ContentType.Text.Plain, HttpStatusCode.NotImplemented)
            }
        }
    }

    get("/get.php") {
        if (isNotMainPort()) return@get
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

        call.respondOutputStream {
            use { output ->
                channelManager.getLiveStreamsPlaylist(
                    output,
                    user,
                    config.getActualBaseUrl(call.request),
                )
                output.flush()
            }
        }
    }
}
