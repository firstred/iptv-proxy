package io.github.firstred.iptvproxy.routes

import com.mayakapps.kache.FileKache
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
import io.github.firstred.iptvproxy.dtos.xtream.XtreamLiveStream
import io.github.firstred.iptvproxy.dtos.xtream.XtreamMovie
import io.github.firstred.iptvproxy.dtos.xtream.XtreamMovieInfoEndpoint
import io.github.firstred.iptvproxy.dtos.xtream.XtreamProfile
import io.github.firstred.iptvproxy.dtos.xtream.XtreamSeries
import io.github.firstred.iptvproxy.dtos.xtream.XtreamSeriesInfoEndpoint
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
import io.github.firstred.iptvproxy.utils.addProxyAuthorizationHeaderIfNecessary
import io.github.firstred.iptvproxy.utils.filterHttpRequestHeaders
import io.github.firstred.iptvproxy.utils.maxRedirects
import io.github.firstred.iptvproxy.utils.toProxiedIconUrl
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.cio.*
import io.sentry.Sentry
import io.sentry.Sentry.captureException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.format
import kotlinx.datetime.format.DateTimeComponents
import kotlinx.datetime.format.FormatStringsInDatetimeFormats
import kotlinx.datetime.format.byUnicodePattern
import kotlinx.serialization.ExperimentalSerializationApi
import org.koin.core.qualifier.named
import org.koin.ktor.ext.inject
import org.koin.mp.KoinPlatform.getKoin
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileNotFoundException
import java.io.Writer
import java.net.URI
import java.net.URISyntaxException
import kotlin.time.Duration

val LOG: Logger = LoggerFactory.getLogger("xtreamApi")

@OptIn(FormatStringsInDatetimeFormats::class, ExperimentalSerializationApi::class)
fun Route.xtreamApi() {
    val channelManager: ChannelManager by inject()
    val channelRepository: ChannelRepository by inject()
    val epgRepository: EpgRepository by inject()
    val xtreamRepository: XtreamRepository by inject()
    val serversByName: IptvServersByName by inject()
    val movieInfoCache: FileKache by inject(named("movie-info"))
    val seriesInfoCache: FileKache by inject(named("series-info"))
    val cacheCoroutineScope: CoroutineScope by inject(named("cache"))

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

        try {
            call.respondTextWriter {
                write("<?xml version=\"1.0\" encoding=\"UTF-8\"?><tv generator-info-name=\"iptv-proxy\">")
                epgRepository.forEachEpgChannelChunk(forUser = user) {
                    it.forEach { row ->
                        write(
                            xml.encodeToString(
                                XmltvChannel.serializer(), row.copy(
                                    icon = row.icon?.copy(
                                        src = row.icon.src?.let {
                                            if (serversByName.values.any { it.config.proxyStream }) it.toProxiedIconUrl(
                                                baseUrl,
                                                encryptedAccount
                                            )
                                            else it
                                        },
                                    ),
                                )
                            )
                        )
                        flush()
                    }
                }
                epgRepository.forEachEpgProgrammeChunk(forUser = user) {
                    it.forEach { row ->
                        write(xml.encodeToString(XmltvProgramme.serializer(), row))
                        flush()
                    }
                }

                write("</tv>")
                flush()
            }
        } catch (_: ChannelWriteException) {
            // Client connection closed
        }
    }

    get("/player_api.php") {
        if (isNotMainPort()) return@get
        if (isNotReady()) return@get

        lateinit var user: IptvUser
        try {
            user = findUserFromXtreamAccountInRoutingContext()
        } catch (_: Throwable) {
            try {
                call.respond(HttpStatusCode.Unauthorized, "Username and/or password incorrect")
            } catch(_: ChannelWriteException) {
                // Client connection closed
            }
            return@get
        }

        val baseUrl = config.getActualBaseUrl(call.request)
        val encryptedAccount = user.toEncryptedAccountHexString()

        when {
            call.request.queryParameters["action"] == "get_live_streams" -> {
                val categoryId = call.request.queryParameters["category_id"]?.toUIntOrNull()

                try {
                    call.respondTextWriter(contentType = ContentType.Application.Json) {
                        write("[")

                        var first = true
                        xtreamRepository.forEachLiveStreamChunk(categoryId = categoryId, forUser = user) { list ->
                            list.forEachIndexed { idx, it ->
                                if (!first) write(",")
                                else first = false

                                write(
                                    json.encodeToString(
                                        XtreamLiveStream.serializer(), it.copy(
                                            streamIcon = if (serversByName[it.server]?.config?.proxyStream
                                                    ?: false
                                            ) it.streamIcon?.toProxiedIconUrl(baseUrl, encryptedAccount)
                                            else it.streamIcon,
                                            server = null,
                                            url = if (serversByName[it.server]?.config?.proxyStream ?: false) "$baseUrl${it.streamType.urlType()}/${user.username}/${user.password}/${it.streamId}.m3u8"
                                            else it.url,
                                        )
                                    )
                                )
                                flush()
                            }
                        }

                        channelRepository.forEachMissingIptvChannelAsLiveStreamChunk { list ->
                            list.forEachIndexed { idx, it ->
                                if (!first) write(",")
                                else first = false

                                write(
                                    json.encodeToString(
                                        XtreamLiveStream.serializer(), it.copy(
                                            streamIcon = if (serversByName[it.server]?.config?.proxyStream
                                                    ?: false
                                            ) it.streamIcon?.toProxiedIconUrl(baseUrl, encryptedAccount)
                                            else it.streamIcon,
                                            server = null,
                                            url = if (serversByName[it.server]?.config?.proxyStream ?: false) "$baseUrl${it.streamType.urlType()}/${user.username}/${user.password}/${it.streamId}.m3u8"
                                            else it.url,
                                        )
                                    )
                                )
                                flush()
                            }
                        }

                        write("]")
                    }
                } catch (_: ChannelWriteException) {
                    // Client connection closed
                }
            }

            call.request.queryParameters["action"] == "get_live_categories" -> {
                try {
                    call.respondTextWriter(contentType = ContentType.Application.Json) { writeLiveCategories() }
                } catch (_: ChannelWriteException) {
                    // Connection closed
                }
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
                                server = null,
                                url = if (serversByName[it.server]?.config?.proxyStream ?: false) "$baseUrl${it.streamType.urlType()}/${user.username}/${user.password}/${it.streamId}.${it.url.toString().substringAfterLast('.')}"
                                else it.url,
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
                if (internalVodId <= 0u) {
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
                    val account = iptvServer.config.accounts?.firstOrNull { null !== it.getXtreamMovieInfoUrl() }
                    val targetUrl = account?.getXtreamMovieInfoUrl()
                    if (null == targetUrl) return@let

                    var movieInfo: XtreamMovieInfoEndpoint? = null

                    val uniqueKey = "baseUrl=$baseUrl|server=${iptvServer.name}|vod_id=$vodId"

                    var movieInfoFile: String? = null
                    try {
                        movieInfoFile = movieInfoCache.get(uniqueKey)
                    } catch (_: FileNotFoundException) {
                    }

                    if (null == movieInfoFile) {
                        lateinit var response: HttpResponse
                        iptvServer.withConnection(iptvServer.config.timeouts.totalMilliseconds) { connection, releaseConnection ->
                            response = connection.httpClient.get("$targetUrl&vod_id=$vodId") {
                                headers {
                                    call.request.headers.filterHttpRequestHeaders()
                                        .entries()
                                        .forEach { (key, value) -> value.forEach { append(key, it) } }
                                    accept(ContentType.Application.Json)
                                    addDefaultClientHeaders(connection.config)
                                }
                            }
                            response = followRedirects(response, connection, call.request.headers).body()
                        }

                        try {
                            movieInfo = response.body<XtreamMovieInfoEndpoint>()
                            movieInfo.let {
                                cacheCoroutineScope.launch { movieInfoCache.putAsync(uniqueKey) { fileName ->
                                    val file = File(fileName)
                                    val text = json.encodeToString(XtreamMovieInfoEndpoint.serializer(), it)
                                    file.writeText(text)

                                    true
                                } }
                            }
                        } catch (e: JsonConvertException) {
                            captureException(e)
                            movieInfo = XtreamMovieInfoEndpoint()
                        }
                    }

                    if (null == movieInfo) {
                        if (null != movieInfoFile) {
                            movieInfo = File(movieInfoFile).readText().let {
                                json.decodeFromString(XtreamMovieInfoEndpoint.serializer(), it)
                            }
                        } else {
                            throw IllegalStateException("Movie info cache not found")
                        }
                    }

                    // First gather all external stream IDs from the response so they can be mapped in one go
                    val foundMovieStreamIds = mutableListOf<UInt>()

                    movieInfo.movieData.let { movieData ->
                        foundMovieStreamIds.add(movieData.streamId.toUInt())
                    }

                    val streamIdMapping = channelRepository.findInternalIdsByExternalIds(foundMovieStreamIds, iptvServer.name)

                    try {
                        call.respond(movieInfo.copy(
                            movieData = movieInfo.movieData.copy(
                                streamId = streamIdMapping[movieInfo.movieData.streamId.toUInt()]?.toInt() ?: 0,
                                cover = movieInfo.movieData.cover?.let { if (it.isNotBlank()) (if (channel.server.config.proxyStream) it.toProxiedIconUrl(baseUrl, encryptedAccount) else it) else "" },
                                categoryId = externalCategoryIdToIdMap[movieInfo.movieData.categoryId]?.toString() ?: "0",
                                categoryIds = try { movieInfo.movieData.categoryIds.map { externalCategoryIdToIdMap[it.toString()]?.toInt() ?: 0 } }
                                catch (e: IllegalArgumentException) {
                                    captureException(e)
                                    movieInfo.movieData.categoryIds
                                }
                            ),
                            info = movieInfo.info.copy(
                                cover = movieInfo.info.cover?.let { if (!it.isNullOrBlank()) (if (channel.server.config.proxyStream) it.toProxiedIconUrl(baseUrl, encryptedAccount) else it) else "" },
                                coverBig = movieInfo.info.coverBig.let { if (!it.isNullOrBlank()) (if (channel.server.config.proxyStream) it.toProxiedIconUrl(baseUrl, encryptedAccount) else it) else "" },
                                movieImage = movieInfo.info.movieImage.let { if (!it.isNullOrBlank()) (if (channel.server.config.proxyStream) it.toProxiedIconUrl(baseUrl, encryptedAccount) else it) else "" },
                                kinopoiskUrl = movieInfo.info.kinopoiskUrl.let { if (!it.isNullOrBlank()) (if (channel.server.config.proxyStream) it.toProxiedIconUrl(baseUrl, encryptedAccount) else it) else "" },
                                backdropPath = movieInfo.info.backdropPath.mapNotNull {
                                    it.let {
                                        if (it.isNotBlank()) (if (channel.server.config.proxyStream) it.toProxiedIconUrl(
                                            baseUrl,
                                            encryptedAccount
                                        ) else it) else null
                                    }
                                },
                            )
                        ))
                    } catch (e: IllegalArgumentException) {
                        captureException(e)
                        call.respond(movieInfo)
                    } catch (_: ChannelWriteException) {
                        // Client closed connection
                        return@get
                    }
                }

                try {
                    call.respondText(
                        "{\"success\": false, \"error\": \"An unknown error occurred\"}",
                        ContentType.Application.Json,
                        HttpStatusCode.InternalServerError,
                    )
                } catch (_: ChannelWriteException) {
                    // Client closed connection
                }
                return@get
            }

            call.request.queryParameters["action"] == "get_series" -> {
                val categoryId = call.request.queryParameters["category_id"]?.toUIntOrNull()

                try {
                    call.respondTextWriter(contentType = ContentType.Application.Json) {
                        write("[")
                        var first = true
                        xtreamRepository.forEachSeriesChunk(categoryId = categoryId, forUser = user) { list ->
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
                } catch (_: ChannelWriteException) {
                    // Client closed connection
                }
            }

            call.request.queryParameters["action"] == "get_series_categories" -> {
                call.response.headers.apply {
                    append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                }

                try {
                    call.respondTextWriter { writeSeriesCategories() }
                } catch (_: ChannelWriteException) {
                    // Client closed connection
                }
            }

            call.request.queryParameters["action"] == "get_series_info" -> {
                val seriesId = call.request.queryParameters["series"]?.toUIntOrNull()
                    ?: call.request.queryParameters["series_id"]?.toUIntOrNull()
                if (null == seriesId || seriesId <= 0u) {
                    try {
                        call.respondText(
                            "{\"success\": false, \"error\": \"A valid Series ID is required\"}",
                            ContentType.Application.Json,
                            HttpStatusCode.BadRequest,
                        )
                    } catch (_: ChannelWriteException) {
                        // Client closed connection
                    }
                    return@get
                }

                // Find server
                val serverName = xtreamRepository.findServerBySeriesId(seriesId)
                if (serverName.isNullOrBlank()) {
                    try {
                        call.respondText(
                            "{\"success\": false, \"error\": \"Series not found\"}",
                            ContentType.Application.Json,
                            HttpStatusCode.BadRequest,
                        )
                    } catch (_: ChannelWriteException) {
                        // Client closed connection
                    }
                    return@get
                }

                val externalCategoryIdToIdMap = xtreamRepository.getExternalCategoryIdToIdMap()

                serversByName[serverName]?.let { iptvServer ->
                    val account = iptvServer.config.accounts?.firstOrNull { null !== it.getXtreamSeriesInfoUrl() }
                    val targetUrl = account?.getXtreamSeriesInfoUrl()
                    if (null == targetUrl) return@get

                    var seriesInfo: XtreamSeriesInfoEndpoint? = null


                    val uniqueKey = "baseUrl=$baseUrl|server=$serverName|series_id=$seriesId"

                    var seriesInfoFile: String? = null
                    try {
                        seriesInfoFile = seriesInfoCache.get(uniqueKey)
                    } catch (_: FileNotFoundException) {
                    }

                    if (null == seriesInfoFile) {
                        lateinit var response: HttpResponse
                        iptvServer.withConnection(iptvServer.config.timeouts.totalMilliseconds) { connection, releaseConnectionEarly ->
                            response = connection.httpClient.get("$targetUrl&series_id=$seriesId") {
                                headers {
                                    accept(ContentType.Application.Json)
                                    addDefaultClientHeaders(connection.config)
                                }
                            }

                            response = followRedirects(response, connection, call.request.headers)
                        }

                        try {
                            seriesInfo = response.body<XtreamSeriesInfoEndpoint>()
                            seriesInfo.let {
                                cacheCoroutineScope.launch { seriesInfoCache.putAsync(uniqueKey) { fileName ->
                                    val file = File(fileName)
                                    val text = json.encodeToString(XtreamSeriesInfoEndpoint.serializer(), it)
                                    file.writeText(text)

                                    true
                                } }
                            }
                        } catch (e: JsonConvertException) {
                            captureException(e)
                            seriesInfo = XtreamSeriesInfoEndpoint()
                        }
                    }

                    if (null == seriesInfo) {
                        if (null != seriesInfoFile) {
                            seriesInfo = File(seriesInfoFile).readText().let {
                                json.decodeFromString(XtreamSeriesInfoEndpoint.serializer(), it)
                            }
                        } else {
                            throw IllegalStateException("Series info cache not found")
                        }
                    }

                    // First gather all external stream IDs from the response so they can be mapped in one go
                    val foundEpisodeStreamIds = mutableListOf<UInt>()

                    seriesInfo.episodes.flatMap { it.value }.forEach { episode ->
                        foundEpisodeStreamIds.add(episode.id.toUInt())
                    }

                    val streamIdMapping = channelRepository.findInternalIdsByExternalIds(foundEpisodeStreamIds, serverName)

                    try {
                        // Rewrite images and remap external IDs to internal IDs
                        call.respond(seriesInfo.copy(
                            seasons = seriesInfo.seasons.map { it.copy(
                                cover = it.cover.let { if (!it.isNullOrBlank()) (if (iptvServer.config.proxyStream) it.toProxiedIconUrl(baseUrl, encryptedAccount) else it) else "" },
                                coverBig = it.coverBig.let { if (!it.isNullOrBlank()) (if (iptvServer.config.proxyStream) it.toProxiedIconUrl(baseUrl, encryptedAccount) else it)  else "" },
                                overview = it.overview.let { if (!it.isNullOrBlank()) (if (iptvServer.config.proxyStream) it.toProxiedIconUrl(baseUrl, encryptedAccount) else it)  else "" },
                                coverTmdb = it.coverTmdb.let { if (!it.isNullOrBlank()) (if (iptvServer.config.proxyStream) it.toProxiedIconUrl(baseUrl, encryptedAccount) else it)  else "" },
                            ) },
                            info = seriesInfo.info.copy(
                                cover = seriesInfo.info.cover?.let { if (it.isNotBlank()) (if (iptvServer.config.proxyStream) it.toProxiedIconUrl(baseUrl, encryptedAccount) else it)  else "" },
                                backdropPath = seriesInfo.info.backdropPath.map { (if (iptvServer.config.proxyStream) it.toProxiedIconUrl(baseUrl, encryptedAccount) else it)  },
                                categoryId = externalCategoryIdToIdMap[seriesInfo.info.categoryId]?.toString() ?: "0",
                                categoryIds = try { seriesInfo.info.categoryIds.map { externalCategoryIdToIdMap[it.toString()] ?: 0u } } catch (e: IllegalArgumentException) {
                                    captureException(e)
                                    seriesInfo.info.categoryIds
                                }
                            ),
                            episodes = seriesInfo.episodes.mapValues { (_, episodes) ->
                                episodes.map { episode ->
                                    episode.copy(
                                        id = streamIdMapping[episode.id.toUInt()]?.toString() ?: "0",
                                        info = episode.info.copy(
                                            movieImage = if (iptvServer.config.proxyStream) episode.info.movieImage.toProxiedIconUrl(baseUrl, encryptedAccount) else episode.info.movieImage,
                                        )
                                    )
                                }
                            }
                        ))
                    } catch (_: ChannelWriteException) {
                        // Client closed connection
                        return@get
                    }
                }

                try {
                    call.respondText(
                        "{\"success\": false, \"error\": \"An unknown error occurred\"}",
                        ContentType.Application.Json,
                        HttpStatusCode.InternalServerError,
                    )
                } catch (_: ChannelWriteException) {
                    // Client closed connection
                }
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
                    captureException(e)
                    programmes = listOf()
                }

                try {
                    call.respond(XtreamEpgList(programmes.map { it.toXtreamEpg().copy(streamId = channelId.toString()) }))
                } catch (_: ChannelWriteException) {
                    // Client closed connection
                }
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
                    captureException(e)
                    programmes = listOf()
                }

                try {
                    call.respond(XtreamEpgList(programmes.map { it.toXtreamEpg().copy(streamId = channelId.toString()) }))
                } catch (_: ChannelWriteException) {
                    // Client closed connection
                }
            }

            call.request.queryParameters["action"].isNullOrBlank() -> {
                try {
                    call.respond(XtreamProfile(userInfo(user), serverInfo(baseUrl)))
                } catch (_: ChannelWriteException) {
                    // Client closed connection
                }
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
            try {
                call.respond(HttpStatusCode.Unauthorized, "Username and/or password incorrect")
            } catch (_: ChannelWriteException) {
                // Client closed connection
            }
            return@get
        }

        val baseUrl = config.getActualBaseUrl(call.request)
        val encryptedAccount = user.toEncryptedAccountHexString()

        when {
            // Get EPG
            call.request.queryParameters["action"] == "get_epg" -> {
                try {
                    call.respondText("[]", ContentType.Application.Json, HttpStatusCode.OK)
                } catch (_: ChannelWriteException) {
                    // Client closed connection
                }
            }

            call.request.queryParameters["action"].isNullOrBlank() -> {
                try {
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
                                xtreamRepository.forEachMovieChunk(forUser = user) { list ->
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
                } catch (_: ChannelWriteException) {
                    // Client closed connection
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
            try {
                call.respond(HttpStatusCode.Unauthorized, "Username and/or password incorrect")
            } catch (_: ChannelWriteException) {
                // Client closed connection
            }
            return@get
        }

        call.response.headers.apply {
            append(HttpHeaders.ContentType, "audio/mpegurl")
            append(HttpHeaders.ContentDisposition, "attachment; filename=playlist_${user.username}.m3u8")
        }

        try {
            call.respondOutputStream { use { output ->
                channelManager.getAllChannelsPlaylist(
                    output,
                    user,
                    config.getActualBaseUrl(call.request),
                )
                output.flush()
            } }
        } catch (_: ChannelWriteException) {
            // Client closed connection
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
                addProxyAuthorizationHeaderIfNecessary()
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
