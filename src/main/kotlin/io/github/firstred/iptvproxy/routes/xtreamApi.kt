package io.github.firstred.iptvproxy.routes

import io.github.firstred.iptvproxy.classes.IptvServerConnection
import io.github.firstred.iptvproxy.classes.IptvUser
import io.github.firstred.iptvproxy.config
import io.github.firstred.iptvproxy.db.repositories.ChannelRepository
import io.github.firstred.iptvproxy.db.repositories.EpgRepository
import io.github.firstred.iptvproxy.db.repositories.XtreamRepository
import io.github.firstred.iptvproxy.di.modules.IptvServersByName
import io.github.firstred.iptvproxy.dotenv
import io.github.firstred.iptvproxy.dtos.xmltv.XmltvChannel
import io.github.firstred.iptvproxy.dtos.xmltv.XmltvProgramme
import io.github.firstred.iptvproxy.dtos.xtream.XtreamCategory
import io.github.firstred.iptvproxy.dtos.xtream.XtreamEpgList
import io.github.firstred.iptvproxy.dtos.xtream.XtreamProfile
import io.github.firstred.iptvproxy.dtos.xtream.XtreamLiveStream
import io.github.firstred.iptvproxy.dtos.xtream.XtreamMovie
import io.github.firstred.iptvproxy.dtos.xtream.XtreamSeries
import io.github.firstred.iptvproxy.dtos.xtream.XtreamServerInfo
import io.github.firstred.iptvproxy.dtos.xtream.XtreamUserInfo
import io.github.firstred.iptvproxy.enums.IptvChannelType
import io.github.firstred.iptvproxy.enums.XtreamOutputFormat
import io.github.firstred.iptvproxy.managers.ChannelManager
import io.github.firstred.iptvproxy.plugins.findUserFromXtreamAccountInRoutingContext
import io.github.firstred.iptvproxy.plugins.isNotMainPort
import io.github.firstred.iptvproxy.plugins.isNotReady
import io.github.firstred.iptvproxy.serialization.json
import io.github.firstred.iptvproxy.serialization.xml
import io.github.firstred.iptvproxy.utils.addDefaultClientHeaders
import io.github.firstred.iptvproxy.utils.filterHttpRequestHeaders
import io.github.firstred.iptvproxy.utils.maxRedirects
import io.github.firstred.iptvproxy.utils.toProxiedIconUrl
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.sentry.Sentry
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.format
import kotlinx.datetime.format.DateTimeComponents
import kotlinx.datetime.format.FormatStringsInDatetimeFormats
import kotlinx.datetime.format.byUnicodePattern
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import org.koin.ktor.ext.inject
import org.koin.mp.KoinPlatform.getKoin
import org.slf4j.LoggerFactory
import java.io.Writer
import java.net.URI
import java.net.URISyntaxException
import kotlin.time.Duration

val LOG = LoggerFactory.getLogger("xtreamApi")

@OptIn(FormatStringsInDatetimeFormats::class)
fun Route.xtreamApi() {
    val channelManager: ChannelManager by inject()
    val channelRepository: ChannelRepository by inject()
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
                val categoryId = call.request.queryParameters["category_id"]?.toUIntOrNull()

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
                val categoryId = call.request.queryParameters["category_id"]?.toUIntOrNull()

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
                val internalVodId = call.request.queryParameters["vod_id"]?.toUIntOrNull() ?: 0u
                if (null == internalVodId || internalVodId <= 0u) {
                    call.respondText(
                        "{\"success\": false, \"error\": \"A valid Movie ID is required\"}",
                        ContentType.Application.Json,
                        HttpStatusCode.BadRequest,
                    )
                    return@get
                }

                // Find server
                val channel = channelRepository.getChannelById(internalVodId)
                val vodId = channel?.externalStreamId ?: 0u
                if (null == channel || vodId <= 0u) {
                    call.respondText(
                        "{\"success\": false, \"error\": \"Series not found\"}",
                        ContentType.Application.Json,
                        HttpStatusCode.BadRequest,
                    )
                    return@get
                }

                val externalCategoryIdToIdMap = xtreamRepository.getExternalCategoryIdToIdMap()

                channel.server.let { iptvServer ->
                    iptvServer.withConnection(iptvServer.config.timeouts.totalMilliseconds) { connection, releaseConnection ->
                        val account = iptvServer.config.accounts?.firstOrNull { null !== it.getXtreamMoviesInfoUrl() }
                        val targetUrl = account?.getXtreamMoviesInfoUrl()
                        if (null == targetUrl) return@withConnection

                        try {
                            var response = connection.httpClient.get("$targetUrl&vod_id=$vodId") {
                                headers {
                                    call.request.headers.filterHttpRequestHeaders().entries().forEach {
                                        (key, value) -> value.forEach { append(key, it) }
                                    }
                                    accept(ContentType.Application.Json)
                                    addDefaultClientHeaders(connection.config)
                                }
                            }
                            response = followRedirects(response, connection, call.request.headers)

                            val responseContent: String = response.body()
                            releaseConnection()

                            val responseElement: JsonElement = json.parseToJsonElement(responseContent)

                            // First gather all external stream IDs from the response so they can be mapped in one go
                            val foundMovieStreamIds = mutableListOf<UInt>()

                            responseElement.jsonObject.entries.forEach {
                                if (it.key == "movie_data") {
                                    it.value.jsonObject.entries.forEach { (key, value) ->
                                        if (key == "stream_id") foundMovieStreamIds.add(value.jsonPrimitive.intOrNull?.toUInt() ?: 0u)
                                    }
                                }
                            }

                            val streamIdMapping = channelRepository.findInternalIdsByExternalIds(foundMovieStreamIds, iptvServer.name)

                            try {
                                call.respond(buildJsonObject {
                                    for ((key, value) in responseElement.jsonObject.entries) {
                                        when (key) {
                                            "info" -> {
                                                put(key, buildJsonObject {
                                                    value.jsonObject.entries.forEach { (infoKey, infoValue) ->
                                                        put(infoKey, when (infoKey) {
                                                            "kinopoisk_url", "cover", "cover_big", "movie_image" -> JsonPrimitive(
                                                                infoValue.jsonPrimitive.contentOrNull?.toProxiedIconUrl(
                                                                    baseUrl,
                                                                    encryptedAccount
                                                                )
                                                            )

                                                            "backdrop_path" -> buildJsonArray {
                                                                infoValue.jsonArray.forEach { backdrop ->
                                                                    add(
                                                                        JsonPrimitive(
                                                                            backdrop.jsonPrimitive.contentOrNull?.toProxiedIconUrl(
                                                                                baseUrl,
                                                                                encryptedAccount
                                                                            )
                                                                        )
                                                                    )
                                                                }
                                                            }

                                                            else -> infoValue
                                                        })
                                                    }
                                                })
                                            }

                                            "movie_data" -> {
                                                put(key, buildJsonObject {
                                                    value.jsonObject.entries.forEach { (movieKey, movieValue) ->
                                                        put(movieKey, when (movieKey) {
                                                            "stream_id"    -> JsonPrimitive(streamIdMapping[movieValue.jsonPrimitive.intOrNull?.toUInt() ?: 0u] ?: 0u)
                                                            "cover"        -> JsonPrimitive(movieValue.jsonPrimitive.contentOrNull?.toProxiedIconUrl(baseUrl, encryptedAccount))
                                                            "category_id"  -> JsonPrimitive(externalCategoryIdToIdMap[movieValue.jsonPrimitive.intOrNull?.toUInt() ?: 0u]?.toString() ?: "0")
                                                            "category_ids" -> try { buildJsonArray {
                                                                movieValue.jsonArray.forEach { categoryId ->
                                                                    add(JsonPrimitive(externalCategoryIdToIdMap[categoryId.jsonPrimitive.intOrNull?.toUInt() ?: 0u] ?: 0u))
                                                                } }
                                                            } catch (e: IllegalArgumentException) {
                                                                Sentry.captureException(e)
                                                                movieValue
                                                            }
                                                            else           -> movieValue
                                                        })
                                                    }
                                                })
                                            }

                                            else -> put(key, value)
                                        }
                                    }
                                })
                            } catch (e: IllegalArgumentException) {
                                Sentry.captureException(e)
                                call.respond(responseElement)
                            }
                        } catch (e: URISyntaxException) {
                            Sentry.captureException(e)
                        }
                    }
                }

                call.respondText(
                    "{\"success\": false, \"error\": \"An unknown error occurred\"}",
                    ContentType.Application.Json,
                    HttpStatusCode.InternalServerError,
                )
                return@get
            }

            call.request.queryParameters["action"] == "get_series" -> {
                val categoryId = call.request.queryParameters["category_id"]?.toUIntOrNull()

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
                val seriesId = call.request.queryParameters["series"]?.toUIntOrNull()
                    ?: call.request.queryParameters["series_id"]?.toUIntOrNull()
                if (null == seriesId || seriesId <= 0u) {
                    call.respondText(
                        "{\"success\": false, \"error\": \"A valid Series ID is required\"}",
                        ContentType.Application.Json,
                        HttpStatusCode.BadRequest,
                    )
                    return@get
                }

                // Find server
                val serverName = xtreamRepository.findServerBySeriesId(seriesId)
                if (serverName.isNullOrBlank()) {
                    call.respondText(
                        "{\"success\": false, \"error\": \"Series not found\"}",
                        ContentType.Application.Json,
                        HttpStatusCode.BadRequest,
                    )
                    return@get
                }

                val externalCategoryIdToIdMap = xtreamRepository.getExternalCategoryIdToIdMap()

                serversByName[serverName]?.let { iptvServer ->
                    iptvServer.withConnection(iptvServer.config.timeouts.totalMilliseconds) { connection, releaseConnectionEarly ->
                        val account =
                            iptvServer.config.accounts?.firstOrNull { null !== it.getXtreamSeriesInfoUrl() }
                        val targetUrl = account?.getXtreamSeriesInfoUrl()
                        if (null == targetUrl) return@withConnection

                        try {
                            var response = connection.httpClient.get("$targetUrl&series_id=$seriesId") {
                                headers {
                                    accept(ContentType.Application.Json)
                                    addDefaultClientHeaders(connection.config)
                                }
                            }

                            response = followRedirects(response, connection, call.request.headers)

                            val responseContent: String = response.body()
                            releaseConnectionEarly()

                            val responseElement: JsonElement = json.parseToJsonElement(responseContent)

                            // First gather all external stream IDs from the response so they can be mapped in one go
                            val foundEpisodeStreamIds = mutableListOf<UInt>()

                            responseElement.jsonObject.entries.forEach {
                                if (it.key == "episodes") {
                                    it.value.jsonObject.entries.forEach { season ->
                                        season.value.jsonArray.forEach { episode ->
                                            foundEpisodeStreamIds.add(episode.jsonObject["id"]?.jsonPrimitive?.intOrNull?.toUInt() ?: 0u)
                                        }
                                    }
                                }
                            }

                            val streamIdMapping = channelRepository.findInternalIdsByExternalIds(foundEpisodeStreamIds, serverName)

                            // Rewrite images and remap external IDs to internal IDs, keeping the old JSON structure intact
                            call.respond(buildJsonObject {
                                for ((key, value) in responseElement.jsonObject.entries) {
                                    when (key) {
                                        "seasons" -> {
                                            put(key, buildJsonArray {
                                                value.jsonArray.forEach { season ->
                                                    try {
                                                        add(buildJsonObject {
                                                            for ((seasonsKey, seasonsValue) in season.jsonObject.entries) {
                                                                when (seasonsKey) {
                                                                    "overview", "cover", "cover_big", "cover_tmdb" -> put(
                                                                        seasonsKey,
                                                                        JsonPrimitive(
                                                                            seasonsValue.jsonPrimitive.contentOrNull?.toProxiedIconUrl(
                                                                                baseUrl,
                                                                                encryptedAccount
                                                                            )
                                                                        )
                                                                    )

                                                                    else -> put(seasonsKey, seasonsValue)
                                                                }
                                                            }
                                                        })
                                                    } catch (e: IllegalArgumentException) {
                                                        Sentry.captureException(e)
                                                        add(season)
                                                    }
                                                }
                                            })
                                        }

                                        "info" -> {
                                            put(key, buildJsonObject {
                                                for ((infoKey, infoValue) in value.jsonObject.entries) {
                                                    when (infoKey) {
                                                        "cover" -> put(
                                                            infoKey,
                                                            JsonPrimitive(
                                                                infoValue.jsonPrimitive.contentOrNull?.toProxiedIconUrl(
                                                                    baseUrl,
                                                                    encryptedAccount
                                                                )
                                                            )
                                                        )

                                                        "backdrop_path" -> {
                                                            put(
                                                                infoKey,
                                                                buildJsonArray {
                                                                    infoValue.jsonArray.forEach { backdrop ->
                                                                        add(
                                                                            JsonPrimitive(
                                                                                backdrop.jsonPrimitive.contentOrNull?.toProxiedIconUrl(
                                                                                    baseUrl,
                                                                                    encryptedAccount
                                                                                )
                                                                            )
                                                                        )
                                                                    }
                                                                })
                                                        }

                                                        "category_id"  -> put(infoKey, JsonPrimitive(externalCategoryIdToIdMap[infoValue.jsonPrimitive.longOrNull ?: 0u]?.toString() ?: "0"))

                                                        "category_ids" -> put(infoKey, try { buildJsonArray {
                                                            infoValue.jsonArray.forEach { categoryId ->
                                                                add(JsonPrimitive(externalCategoryIdToIdMap[categoryId.jsonPrimitive.intOrNull?.toUInt() ?: 0u] ?: 0u))
                                                            } }
                                                        } catch (e: IllegalArgumentException) {
                                                            Sentry.captureException(e)
                                                            infoValue
                                                        })

                                                        else -> put(infoKey, infoValue)
                                                    }
                                                }
                                            })
                                        }

                                        "episodes" -> {
                                            put(key, buildJsonObject {
                                                value.jsonObject.entries.forEach { (seasonNumber, season) ->
                                                    try {
                                                        put(seasonNumber, buildJsonArray {
                                                            for (episode in season.jsonArray) {
                                                                add(buildJsonObject {
                                                                    for ((episodeKey, episodeValue) in episode.jsonObject.entries) {
                                                                        when (episodeKey) {
                                                                            "id" -> put(
                                                                                episodeKey,
                                                                                JsonPrimitive(streamIdMapping[episodeValue.jsonPrimitive.intOrNull?.toUInt() ?: 0u]?.toString() ?: ""),
                                                                            )

                                                                            "info" -> {
                                                                                try {
                                                                                    put("info", buildJsonObject {
                                                                                        for ((infoKey, infoValue) in episodeValue.jsonObject.entries) {
                                                                                            when (infoKey) {
                                                                                                "movie_image" -> put(
                                                                                                    infoKey,
                                                                                                    JsonPrimitive(
                                                                                                        infoValue.jsonPrimitive.contentOrNull?.toProxiedIconUrl(
                                                                                                            baseUrl,
                                                                                                            encryptedAccount
                                                                                                        )
                                                                                                    )
                                                                                                )

                                                                                                else -> put(
                                                                                                    infoKey,
                                                                                                    infoValue
                                                                                                )
                                                                                            }
                                                                                        }
                                                                                    })
                                                                                } catch (e: IllegalArgumentException) {
                                                                                    Sentry.captureException(e)
                                                                                    put("info", episode)
                                                                                }
                                                                            }

                                                                            else -> put(episodeKey, episodeValue)
                                                                        }
                                                                    }
                                                                })
                                                            }
                                                        })
                                                    } catch (e: IllegalArgumentException) {
                                                        Sentry.captureException(e)
                                                        put(seasonNumber, season)
                                                    }
                                                }
                                            })
                                        }

                                        else -> put(key, value)
                                    }
                                }
                            })
                        } catch (e: URISyntaxException) {
                            Sentry.captureException(e)
                        }
                    }
                }

                call.respondText(
                    "{\"success\": false, \"error\": \"An unknown error occurred\"}",
                    ContentType.Application.Json,
                    HttpStatusCode.InternalServerError,
                )
                return@get
            }

            // Get EPG
            call.request.queryParameters["action"] == "get_short_epg" -> {
                val channelId = call.request.queryParameters["stream_id"]?.toUIntOrNull()
                if (channelId == null || channelId <= 0u) {
                    call.respondText(
                        "{\"success\": false, \"error\": \"A valid Channel ID is required\"}",
                        ContentType.Application.Json,
                        HttpStatusCode.BadRequest,
                    )
                    return@get
                }
                val limit = (call.request.queryParameters["limit"]?.toIntOrNull() ?: 4).coerceIn(1, 100)

                lateinit var programmes: List<XmltvProgramme>
                try {
                    programmes = epgRepository.getProgrammesForChannelId(channelId, limit)
                } catch (e: Throwable) {
                    Sentry.captureException(e)
                    programmes = listOf()
                }

                call.respond(XtreamEpgList(programmes.map { it.toXtreamEpg().copy(streamId = channelId.toString()) }))
            }

            // EPG date table
            listOf("get_simple_date_table", "get_simple_data_table").contains(call.request.queryParameters["action"]) -> {
                val channelId = call.request.queryParameters["stream_id"]?.toUIntOrNull()
                if (channelId == null || channelId <= 0u) {
                    call.respondText(
                        "{\"success\": false, \"error\": \"A valid Channel ID is required\"}",
                        ContentType.Application.Json,
                        HttpStatusCode.BadRequest,
                    )
                    return@get
                }

                lateinit var programmes: List<XmltvProgramme>
                try {
                    programmes = epgRepository.getProgrammesForChannelId(
                        channelId,
                        Int.MAX_VALUE,
                        Instant.fromEpochMilliseconds(0)
                    )
                } catch (e: Throwable) {
                    Sentry.captureException(e)
                    programmes = listOf()
                }

                call.respond(XtreamEpgList(programmes.map { it.toXtreamEpg().copy(streamId = channelId.toString()) }))
            }

            call.request.queryParameters["action"].isNullOrBlank() -> {
                call.respond(XtreamProfile(userInfo(user), serverInfo(baseUrl)))
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
                        write("},")
                        write("\"available_channels\": {")
                            var first = true
                            xtreamRepository.forEachLiveStreamChunk { list ->
                                list.forEachIndexed { idx, it ->
                                    if (!first) write(",")
                                    else first = false
                                    write("\"${it.streamId}\": ")
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
                                    write("\"${it.streamId}\": ")
                                    write(json.encodeToString(XtreamMovie.serializer(), it.copy(
                                        streamIcon = if (serversByName[it.server]?.config?.proxyStream ?: false) it.streamIcon.toProxiedIconUrl(baseUrl, encryptedAccount)
                                        else it.streamIcon,
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

private suspend fun followRedirects(
    response: HttpResponse,
    connection: IptvServerConnection,
    headers: Headers,
): HttpResponse {
    var newResponse = response
    var redirects = 0
    var newLocation = newResponse.headers["Location"] ?: ""

    while (newLocation.isNotBlank() && redirects < maxRedirects) {
        // Follow redirects
        newResponse = connection.httpClient.get(newLocation) {
            headers {
                headers.filterHttpRequestHeaders().entries().forEach { (key, value) -> value.forEach { append(key, it) } }
                accept(ContentType.Application.Json)
                addDefaultClientHeaders(connection.config)
            }
        }
        newLocation = newResponse.headers["Location"] ?: ""

        try {
            (URI(newLocation))
        } catch (_: URISyntaxException) {
            LOG.warn("Invalid redirect URI found: $newLocation")
            if (!config.sentry?.dsn.isNullOrBlank()) {
                Sentry.captureMessage("Invalid redirect URI found: $newLocation")
            }
        }

        redirects++
    }
    return newResponse
}

private fun Writer.writeSeriesCategories() {
    val xtreamRepository: XtreamRepository = getKoin().get()

    write("[")
    var first = true
    xtreamRepository.forEachCategoryChunk(type = IptvChannelType.series) { list ->
        list.forEachIndexed { idx, it ->
            if (!first) write(",")
            else first = false

            write(json.encodeToString(XtreamCategory.serializer(), it))
            flush()
        }
    }

    write("]")
}

private fun Writer.writeMovieCategories() {
    val xtreamRepository: XtreamRepository = getKoin().get()

    write("[")
    var first = true
    xtreamRepository.forEachCategoryChunk(type = IptvChannelType.movie) { list ->
        list.forEachIndexed { idx, it ->
            if (!first) write(",")
            else first = false

            write(json.encodeToString(XtreamCategory.serializer(), it))
            flush()
        }
    }

    write("]")
}

private fun Writer.writeLiveCategories() {
    val xtreamRepository: XtreamRepository = getKoin().get()

    write("[")
    var first = true
    xtreamRepository.forEachCategoryChunk(type = IptvChannelType.live) { list ->
        list.forEachIndexed { idx, it ->
            if (!first) write(",")
            else first = false

            write(json.encodeToString(XtreamCategory.serializer(), it))
            flush()
        }
    }

    write("]")
}

@OptIn(FormatStringsInDatetimeFormats::class)
private fun serverInfo(baseUrl: URI): XtreamServerInfo = XtreamServerInfo(
    timezone = dotenv.get("TZ") ?: "UTC",
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
