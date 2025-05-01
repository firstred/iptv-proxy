package io.github.firstred.iptvproxy.routes

import io.github.firstred.iptvproxy.config
import io.github.firstred.iptvproxy.db.repositories.EpgRepository
import io.github.firstred.iptvproxy.db.repositories.XtreamRepository
import io.github.firstred.iptvproxy.di.modules.IptvServersByName
import io.github.firstred.iptvproxy.dtos.xmltv.XmltvChannel
import io.github.firstred.iptvproxy.dtos.xmltv.XmltvProgramme
import io.github.firstred.iptvproxy.dtos.xtream.XtreamInfo
import io.github.firstred.iptvproxy.dtos.xtream.XtreamLiveStream
import io.github.firstred.iptvproxy.dtos.xtream.XtreamLiveStreamCategory
import io.github.firstred.iptvproxy.dtos.xtream.XtreamMovie
import io.github.firstred.iptvproxy.dtos.xtream.XtreamMovieCategory
import io.github.firstred.iptvproxy.dtos.xtream.XtreamSeries
import io.github.firstred.iptvproxy.dtos.xtream.XtreamSeriesCategory
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
import org.koin.mp.KoinPlatform.getKoin
import java.io.Writer
import java.net.URI
import kotlin.time.Duration

@OptIn(FormatStringsInDatetimeFormats::class)
fun Route.xtreamApi() {
    val channelManager: ChannelManager by inject()
    val epgRepository: EpgRepository by inject()
    val xtreamRepository: XtreamRepository by inject()
    val serversByName: IptvServersByName by inject()

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
            append(HttpHeaders.ContentDisposition, "attachment; filename=xmltv_${user.username}.xml")
        }

        val baseUrl = config.getActualBaseUrl(call.request).toString()
        val encryptedAccount = user.toEncryptedAccountHexString()
        call.respondTextWriter {
            write("<?xml version=\"1.0\" encoding=\"UTF-8\"?><tv generator-info-name=\"iptv-proxy\">")
            for (server in config.servers) {
                epgRepository.forEachEpgChannelChunk(server.name) {
                    it.forEach { row ->
                        write(
                            xml.encodeToString(
                                XmltvChannel.serializer(), row.copy(
                                    icon = row.icon?.copy(
                                        src = row.icon.src?.let {
                                            if (server.proxyStream) it.toProxiedIconUrl(baseUrl, encryptedAccount)
                                            else it
                                        },
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
        val encryptedAccount = user.toEncryptedAccountHexString()

        when {
            call.request.queryParameters["action"] == "get_live_streams" -> {
                val categoryId = call.request.queryParameters["category_id"]?.toLongOrNull()

                call.respondTextWriter(contentType = ContentType.Application.Json) {
                    write("[")
                    var first = true
                    xtreamRepository.forEachLiveStreamChunk(categoryId = categoryId) { list ->
                        list.forEachIndexed { idx, it ->
                            if (!first) write(",")
                            else first = false

                            write(json.encodeToString(XtreamLiveStream.serializer(), it.copy(
                                streamIcon = if (serversByName[it.server]?.config?.proxyStream ?: false) it.streamIcon?.toProxiedIconUrl(baseUrl, encryptedAccount)
                                else it.streamIcon,
                                server = null,
                            )))
                            flush()
                        }
                    }

                    write("]")
                }
            }

            call.request.queryParameters["action"] == "get_live_categories" -> {
                call.respondTextWriter(contentType = ContentType.Application.Json) { writeLiveCategories() }
            }

            listOf("get_vod_streams", "get_movie_streams", "get_movies_streams").contains(call.request.queryParameters["action"]) -> {
                val categoryId = call.request.queryParameters["category_id"]?.toLongOrNull()

                call.respondTextWriter(contentType = ContentType.Application.Json) {
                    write("[")
                    var first = true
                    xtreamRepository.forEachMovieChunk(categoryId = categoryId) { list ->
                        list.forEachIndexed { idx, it ->
                            if (!first) write(",")
                            else first = false

                            write(json.encodeToString(XtreamMovie.serializer(), it.copy(
                                streamIcon = if (serversByName[it.server]?.config?.proxyStream ?: false) it.streamIcon.toProxiedIconUrl(baseUrl, encryptedAccount)
                                else it.streamIcon,
                                server = null
                            )))
                            flush()
                        }
                    }

                    write("]")
                }
            }

            listOf("get_vod_categories", "get_movie_categories", "get_movies_categories").contains(call.request.queryParameters["action"]) -> {
                call.respondTextWriter(contentType = ContentType.Application.Json) { writeMovieCategories() }
            }

            listOf("get_vod_info", "get_movie_info", "get_movies_info").contains(call.request.queryParameters["action"]) -> {
                call.respondText("[]]", ContentType.Application.Json, HttpStatusCode.OK)
            }

            call.request.queryParameters["action"] == "get_series" -> {
                val categoryId = call.request.queryParameters["category_id"]?.toLongOrNull()

                call.respondTextWriter(contentType = ContentType.Application.Json) {
                    write("[")
                    var first = true
                    xtreamRepository.forEachSeriesChunk(categoryId = categoryId) { list ->
                        list.forEachIndexed { idx, it ->
                            if (!first) write(",")
                            else first = false

                            write(json.encodeToString(XtreamSeries.serializer(), it.copy(
                                cover = if (serversByName[it.server]?.config?.proxyStream ?: false) it.cover.toProxiedIconUrl(baseUrl, encryptedAccount)
                                else it.cover,
                                backdropPath = if (serversByName[it.server]?.config?.proxyStream ?: false) it.backdropPath?.map { it?.toProxiedIconUrl(baseUrl, encryptedAccount) }
                                else it.backdropPath,
                                server = null,
                            )))
                            flush()
                        }
                    }

                    write("]")
                }
            }

            call.request.queryParameters["action"] == "get_series_categories" -> {
                call.response.headers.apply {
                    append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                }

                call.respondTextWriter { writeSeriesCategories() }
            }

            call.request.queryParameters["action"] == "get_series_info" -> {
                call.respondText("[]]", ContentType.Application.Json, HttpStatusCode.OK)
            }

            // Get EPG
            call.request.queryParameters["action"] == "get_short_epg" -> {
                call.respondText("Not implemented", ContentType.Text.Plain, HttpStatusCode.NotImplemented)
            }

            // EPG date table
            call.request.queryParameters["action"] == "get_simple_date_table" -> {
                call.respondText("Not implemented", ContentType.Text.Plain, HttpStatusCode.NotImplemented)
            }

            call.request.queryParameters["action"].isNullOrBlank() -> {
                call.respond(XtreamInfo(userInfo(user), serverInfo(baseUrl)))
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

        val baseUrl = config.getActualBaseUrl(call.request)
        val encryptedAccount = user.toEncryptedAccountHexString()

        when {
            // Get EPG
            call.request.queryParameters["action"] == "get_epg" -> {
                call.respondText("[]", ContentType.Application.Json, HttpStatusCode.OK)
            }

            call.request.queryParameters["action"].isNullOrBlank() -> {
                call.respondTextWriter(contentType = ContentType.Application.Json) {
                    write("{")
                        write("\"user_info\":")
                        write(json.encodeToString(XtreamUserInfo.serializer(), userInfo(user)))
                        flush()
                        write(",")
                        write("\"server_info\":")
                        write(json.encodeToString(XtreamServerInfo.serializer(), serverInfo(config.getActualBaseUrl(call.request))))
                        flush()
                        write(",")
                        write("\"categories\": {")
                            write("\"live\": ")
                            writeLiveCategories()
                            flush()
                            write(",")
                            write("\"movies\": ")
                            writeMovieCategories()
                            flush()
                            write(",")
                            write("\"series\": ")
                            writeSeriesCategories()
                            flush()
                        write("},")
                        write("\"available_channels\": {")
                            var first = true
                            xtreamRepository.forEachLiveStreamChunk { list ->
                                list.forEachIndexed { idx, it ->
                                    if (!first) write(",")
                                    else first = false
                                    write("\"${it.categoryId}\": ")
                                    write(json.encodeToString(XtreamLiveStream.serializer(), it.copy(
                                        streamIcon = if (serversByName[it.server]?.config?.proxyStream ?: false) it.streamIcon?.toProxiedIconUrl(baseUrl, encryptedAccount)
                                        else it.streamIcon,
                                        server = null,
                                    )))
                                    flush()
                                }
                            }
                            write(",")
                            first = true
                            xtreamRepository.forEachMovieChunk { list ->
                                list.forEachIndexed { idx, it ->
                                    if (!first) write(",")
                                    else first = false
                                    write("\"${it.categoryId}\": ")
                                    write(json.encodeToString(XtreamMovie.serializer(), it.copy(
                                        streamIcon = if (serversByName[it.server]?.config?.proxyStream ?: false) it.streamIcon.toProxiedIconUrl(baseUrl, encryptedAccount)
                                        else it.streamIcon,
                                        server = null,
                                    )))
                                    flush()
                                }
                            }
                            write(",")
                            first = true
                            xtreamRepository.forEachSeriesChunk { list ->
                                list.forEachIndexed { idx, it ->
                                    if (!first) write(",")
                                    else first = false
                                    write("\"${it.categoryId}\": ")
                                    write(json.encodeToString(XtreamSeries.serializer(), it.copy(
                                        cover = if (serversByName[it.server]?.config?.proxyStream ?: false) it.cover.toProxiedIconUrl(baseUrl, encryptedAccount)
                                        else it.cover,
                                        backdropPath = if (serversByName[it.server]?.config?.proxyStream ?: false) it.backdropPath?.map { it?.toProxiedIconUrl(baseUrl, encryptedAccount) }
                                        else it.backdropPath,
                                        server = null,
                                    )))
                                    flush()
                                }
                            }
                        write("}")
                    write("}")
                    flush()
                }
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
            append(HttpHeaders.ContentDisposition, "attachment; filename=playlist_${user.username}.m3u8")
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

private fun Writer.writeSeriesCategories() {
    val xtreamRepository: XtreamRepository = getKoin().get()

    write("[")
    var first = true
    xtreamRepository.forEachSeriesCategoryChunk { list ->
        list.forEachIndexed { idx, it ->
            if (!first) write(",")
            else first = false

            write(json.encodeToString(XtreamSeriesCategory.serializer(), it))
            flush()
        }
    }

    write("]")
}

private fun Writer.writeMovieCategories() {
    val xtreamRepository: XtreamRepository = getKoin().get()

    write("[")
    var first = true
    xtreamRepository.forEachMovieCategoryChunk { list ->
        list.forEachIndexed { idx, it ->
            if (!first) write(",")
            else first = false

            write(json.encodeToString(XtreamMovieCategory.serializer(), it))
            flush()
        }
    }

    write("]")
}

private fun Writer.writeLiveCategories() {
    val xtreamRepository: XtreamRepository = getKoin().get()

    write("[")
    var first = true
    xtreamRepository.forEachLiveStreamCategoryChunk { list ->
        list.forEachIndexed { idx, it ->
            if (!first) write(",")
            else first = false

            write(json.encodeToString(XtreamLiveStreamCategory.serializer(), it))
            flush()
        }
    }

    write("]")
}

@OptIn(FormatStringsInDatetimeFormats::class)
private fun serverInfo(baseUrl: URI): XtreamServerInfo = XtreamServerInfo(
    timezone = "UTC",
    timeNow = Clock.System.now().format(DateTimeComponents.Format {
        byUnicodePattern("yyyy-MM-dd HH:mm:ss")
    }),
    timestampNow = (Clock.System.now() + Duration.parse("P365D")).epochSeconds.toString(),
    process = true,
    url = baseUrl.host,
    port = if ("http" == baseUrl.scheme) (baseUrl.port.let { if (it in 1..65535) it else 80 }.toString()) else "80",
    protocol = baseUrl.scheme,
    httpsPort = if ("https" == baseUrl.scheme) (baseUrl.port.let { if (it in 1..65535) it else 443 }.toString()) else "",
    rtmpPort = "",
)

private fun userInfo(user: IptvUser): XtreamUserInfo = XtreamUserInfo(
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
)
