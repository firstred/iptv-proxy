package io.github.firstred.iptvproxy.plugins

import com.mayakapps.kache.FileKache
import io.github.firstred.iptvproxy.classes.IptvChannel
import io.github.firstred.iptvproxy.classes.IptvUser
import io.github.firstred.iptvproxy.config
import io.github.firstred.iptvproxy.db.repositories.ChannelRepository
import io.github.firstred.iptvproxy.di.modules.IptvUsersByName
import io.github.firstred.iptvproxy.listeners.HealthListener
import io.github.firstred.iptvproxy.managers.ChannelManager
import io.github.firstred.iptvproxy.managers.UserManager
import io.github.firstred.iptvproxy.routes.hls
import io.github.firstred.iptvproxy.routes.images
import io.github.firstred.iptvproxy.routes.xtreamApi
import io.github.firstred.iptvproxy.utils.addDefaultClientHeaders
import io.github.firstred.iptvproxy.utils.addHeadersFromPlaylistProps
import io.github.firstred.iptvproxy.utils.aesDecryptFromHexString
import io.github.firstred.iptvproxy.utils.aesEncryptToHexString
import io.github.firstred.iptvproxy.utils.appendQueryParameters
import io.github.firstred.iptvproxy.utils.filterAndAppendHttpRequestHeaders
import io.github.firstred.iptvproxy.utils.filterHttpRequestHeaders
import io.github.firstred.iptvproxy.utils.filterHttpResponseHeaders
import io.github.firstred.iptvproxy.utils.forwardProxyUser
import io.github.firstred.iptvproxy.utils.hasSupportedScheme
import io.github.firstred.iptvproxy.utils.isHlsPlaylist
import io.github.firstred.iptvproxy.utils.maxCacheableVideoChunkSize
import io.github.firstred.iptvproxy.utils.maxRedirects
import io.github.firstred.iptvproxy.utils.sendBasicAuth
import io.github.firstred.iptvproxy.utils.sendUserAgent
import io.github.firstred.iptvproxy.utils.toEncodedJavaURI
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.cio.*
import io.ktor.utils.io.*
import io.ktor.utils.io.jvm.javaio.*
import io.sentry.Sentry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.io.readByteArray
import org.koin.core.qualifier.named
import org.koin.java.KoinJavaComponent.getKoin
import org.koin.ktor.ext.inject
import org.slf4j.LoggerFactory
import java.io.File
import java.io.OutputStream
import java.net.URI
import java.net.URISyntaxException
import kotlin.text.Charsets.UTF_8

private val LOG = LoggerFactory.getLogger("RoutingPlugin")

fun Application.configureRouting() {
    routing {
        images()
        xtreamApi()
        hls()
    }
}

fun RoutingContext.isMainPort() = config.port.toInt() == call.request.local.localPort
fun RoutingContext.isNotMainPort() = !isMainPort()
fun RoutingContext.isHealthcheckPort() = config.healthcheckPort?.toInt() == call.request.local.localPort
fun RoutingContext.isNotHealthcheckPort() = !isHealthcheckPort()
fun RoutingContext.isMetricsPort() = config.metricsPort?.toInt() == call.request.local.localPort
fun RoutingContext.isNotMetricsPort() = !isMetricsPort()

suspend fun RoutingContext.isReady(): Boolean {
    val health: HealthListener by getKoin().inject()

    if (!health.isReady()) {
        try {
            call.respond(HttpStatusCode.ServiceUnavailable, "Service is not ready")
        } catch (_: ChannelWriteException) {
            // Client closed connection
        }
        return false
    }

    return true
}

suspend fun RoutingContext.isNotReady() = !isReady()

suspend fun RoutingContext.isLive(): Boolean {
    val health: HealthListener by getKoin().inject()

    if (!health.isLive()) {
        try {
            call.respond(HttpStatusCode.ServiceUnavailable, "Service is not ready")
        } catch (_: ChannelWriteException) {
            // Client closed connection
        }
        return false
    }

    return true
}

suspend fun RoutingContext.isNotLive() = !isLive()

fun RoutingContext.findUserFromUrlInRoutingContext(): IptvUser {
    val userManager: UserManager by getKoin().inject()

    val username = call.parameters["username"]
    val password = call.parameters["password"]

    if (!username.isNullOrBlank() && !password.isNullOrBlank()) {
        userManager.getUserOrNull(username, password)?.let { return it }
    }

    return findUserFromProxyHeader(userManager)
}

fun RoutingContext.findUserFromXtreamAccountInRoutingContext(): IptvUser {
    val userManager: UserManager by getKoin().inject()

    val username = call.queryParameters["username"]
    val password = call.queryParameters["password"]

    if (!username.isNullOrBlank() && !password.isNullOrBlank()) {
        userManager.getUserOrNull(username, password)?.let { return it }
    }

    return findUserFromProxyHeader(userManager)
}

private fun RoutingContext.findUserFromProxyHeader(userManager: UserManager): IptvUser {
    val proxyUsername = config.getForwardedValues(call.request.headers.getAll("Forwarded")).proxyUser
    val username = proxyUsername ?: call.parameters["username"]
    val password = proxyUsername?.let { getKoin().get<IptvUsersByName>()[it]?.password } ?: call.parameters["password"]

    return userManager.getUser(username.toString(), password.toString())
}

fun RoutingContext.findUserFromEncryptedAccountInRoutingContext(): IptvUser {
    val userManager: UserManager by getKoin().inject()

    val encryptedAccount = call.parameters["encryptedaccount"]
    encryptedAccount?.aesDecryptFromHexString()?.let { decrypted ->
        val username = decrypted.substringBefore('_')
        val password = decrypted.substringAfter('_')

        userManager.getUserOrNull(username, password)?.let { return it }
    }

    return findUserFromProxyHeader(userManager)
}

fun Route.proxyRemotePlaylist() {
    val channelManager by inject<ChannelManager>()

    get(Regex("""^(?<username>[^/]+)/(?<password>[^/]+)/(?<streamid>[^.]+)\.(?<extension>.*)$""")) {
        if (isNotMainPort()) return@get
        if (isNotReady()) return@get

        lateinit var user: IptvUser
        try {
            user = findUserFromUrlInRoutingContext()
        } catch (_: Throwable) {
            try {
                call.respond(HttpStatusCode.Unauthorized, "Username and/or password incorrect")
            } catch (_: ChannelWriteException) {
                // Client closed connection
            }
            return@get
        }

        val channelId = (call.parameters["streamid"] ?: run {
            try {
                call.respond(HttpStatusCode.BadRequest, "Missing Stream ID")
            } catch (_: ChannelWriteException) {
                // Client closed connection
            }
            return@get
        }).toUInt()

        withUserPermit(user) {
            call.response.headers.apply {
                append(HttpHeaders.ContentType, "audio/mpegurl")
                append(
                    HttpHeaders.ContentDisposition,
                    "attachment; filename=${call.request.path().substringAfterLast("/")}",
                )
            }

            try {
                call.respondText(
                    channelManager.getChannelPlaylist(
                        channelId,
                        user,
                        config.getActualBaseUrl(call.request),
                        call.request.headers.filterHttpRequestHeaders(),
                        call.request.queryParameters,
                    ) { headers ->
                        headers.filterHttpResponseHeaders().entries()
                            .forEach { (key, value) -> value.forEach { call.response.headers.append(key, it) } }
                    }
                )
            } catch (_: ChannelWriteException) {
                // Client closed connection
            }
        }
    }
}

suspend fun Route.rewriteRemotePlaylist(
    outputStream: OutputStream,
    user: IptvUser,
    channel: IptvChannel,
    baseUrl: URI,
    remoteUrl: URI,
    additionalHeaders: Headers = headersOf(),
    additionalQueryParameters: Parameters = parametersOf(),
    headersCallback: ((Headers) -> Unit)? = null,
) {
    val outputWriter = outputStream.bufferedWriter(UTF_8)
    val server = channel.server

    server.withConnection(server.config.timeouts.totalMilliseconds) { connection, releaseConnectionEarly ->
        lateinit var response: HttpResponse
        val url = remoteUrl
        response = connection.httpClient.get(
            url.appendQueryParameters(additionalQueryParameters).toString(),
        ) {
            headers {
                additionalHeaders.forEach { key, values -> values.forEach { value -> append(key, value) } }
                forwardProxyUser(connection.config)
                sendUserAgent(connection.config)
                if (null != connection.config.account) sendBasicAuth(connection.config.account)
                addHeadersFromPlaylistProps(channel.m3uProps, channel.vlcOpts)
            }
        }
        headersCallback?.invoke(response.headers)

        var responseURI = url

        var redirects = 0

        // Check if response is a redirect
        while (null != response.headers["Location"] && redirects < maxRedirects) {
            val location = response.headers["Location"] ?: break

            // Follow redirects
            response = connection.httpClient.get(location) {
                headers {
                    additionalHeaders.forEach { key, values -> values.forEach { value -> append(key, value) } }
                    forwardProxyUser(connection.config)
                    sendUserAgent(connection.config)
                    if (null != connection.config.account) sendBasicAuth(connection.config.account)
                    addHeadersFromPlaylistProps(channel.m3uProps, channel.vlcOpts)
                }
            }
            response.body<String>()
            try {
                responseURI = responseURI.resolve(location)
            } catch (_: URISyntaxException) {
            }

            redirects++
        }

        val infoLines = response.body<String>().lines()

        releaseConnectionEarly()

        try {
            for (infoLine in infoLines) {
                var infoLine = infoLine

                when {
                    // Only rewrite direct media URLs this time, no icons etc.
                    !infoLine.trim(' ').startsWith("#") && infoLine.trim(' ').isNotBlank() -> {
                        var newInfoLine = infoLine.trim(' ')

                        // This is a stream URL
                        if (!newInfoLine.startsWith("http://") && !newInfoLine.startsWith("https://")) {
                            newInfoLine = responseURI.resolve(newInfoLine).toString()
                        }

                        try {
                            val remoteUrl = Url(newInfoLine).toEncodedJavaURI()
                            val fileName = remoteUrl.path.substringAfterLast('/', "").substringBeforeLast('.')
                            val extension = remoteUrl.path.substringAfterLast('.', "")

                            infoLine =
                                "${baseUrl}hls/${user.toEncryptedAccountHexString()}/${remoteUrl.aesEncryptToHexString()}/${channel.id}/$fileName.$extension"
                        } catch (e: URISyntaxException) {
                            LOG.warn(
                                "[{}] Error while parsing stream URL: {}, error: {}",
                                user.username,
                                infoLine,
                                e.message,
                            )
                        }
                    }
                }

                outputWriter.write("$infoLine\n")
                outputWriter.flush()
            }
        } catch (_: ChannelWriteException) {
            // Client closed connection
        }
    }
}

fun Route.proxyRemoteVideo() {
    val channelRepository: ChannelRepository by inject()

    get(Regex("""^(?<username>[^/]+)/(?<password>[^/]+)/(?<channelid>[^.]+?)(?:\.(?<extension>.*))?$""")) {
        if (isNotMainPort()) return@get
        if (isNotReady()) return@get
        val routingContext = this

        lateinit var user: IptvUser
        try {
            user = findUserFromUrlInRoutingContext()
        } catch (_: Throwable) {
            try {
                call.respond(HttpStatusCode.Unauthorized, "Username and/or password incorrect")
            } catch (_: ChannelWriteException) {
                // Client closed connection
            }
            return@get
        }

        val streamId = call.parameters["channelid"] ?: run {
            try {
                call.respond(HttpStatusCode.BadRequest, "Missing Stream ID")
            } catch (_: ChannelWriteException) {
                // Client closed connection
            }
            return@get
        }
        val channel = channelRepository.getChannelById(streamId.toUInt()) ?: run {
            try{
                call.respond(HttpStatusCode.NotFound, "Channel not found")
            } catch (_: ChannelWriteException) {
                // Client closed connection
            }
            return@get
        }

        streamRemoteVideoChunk(user, channel, routingContext)
    }
}

fun Route.proxyRemoteHlsStream() {
    val channelRepository: ChannelRepository by inject()

    get(Regex("""^(?<encryptedaccount>[0-9a-fA-F]+)/(?<encryptedremoteurl>[0-9a-fA-F]+)/(?<channelid>[^/]+)/(?<filename>[^.]+?)(?:\.(?<extension>.*))?$""")) {
        if (isNotMainPort()) return@get
        val user = findUserFromEncryptedAccountInRoutingContext()
        val routingContext = this

        val channelId = (call.parameters["channelid"] ?: "").toLong()
        if (channelId < 0) {
            try {
                call.respond(HttpStatusCode.BadRequest, "Invalid channel ID")
            } catch (_: ChannelWriteException) {
                // Client closed connection
            }
            return@get
        }
        val channel = channelRepository.getChannelById(channelId.toUInt()) ?: run {
            try {
                call.respond(HttpStatusCode.NotFound, "Channel not found")
            } catch (_: ChannelWriteException) {
                // Client closed connection
            }
            return@get
        }

        val remoteUrl = call.parameters["encryptedremoteurl"]?.aesDecryptFromHexString() ?: run {
            try {
                call.respond(HttpStatusCode.BadRequest, "Invalid remote URL")
            } catch (_: ChannelWriteException) {
                // Client closed connection
            }
            return@get
        }
        if (!remoteUrl.hasSupportedScheme()) {
            try {
                call.respond(HttpStatusCode.BadRequest, "Invalid remote URL")
            } catch (_: ChannelWriteException) {
                // Client closed connection
            }
            return@get
        }

        val remoteURI = Url(remoteUrl).toEncodedJavaURI()

        if (remoteUrl.isHlsPlaylist()) {
            try {
                call.respondOutputStream(
                    contentType = ContentType("application", "x-mpegurl"),
                    status = HttpStatusCode.OK,
                ) {
                    rewriteRemotePlaylist(
                        this,
                        user,
                        channel,
                        config.getActualBaseUrl(call.request),
                        remoteURI,
                    )
                }
            } catch (_: ChannelWriteException) {
                // Client closed connection
            }
        }

        streamRemoteVideoChunk(
            user,
            channel,
            routingContext,
            remoteURI,
        )
    }
}

private suspend fun RoutingContext.streamRemoteVideoChunk(
    user: IptvUser,
    channel: IptvChannel,
    routingContext: RoutingContext,
    remoteUrl: URI = channel.url,
) {
    val videoChunkCache: FileKache = getKoin().get(named("video-chunks"))
    val cacheCoroutineScope: CoroutineScope by getKoin().inject(named("cache"))
    var responseURI = remoteUrl.appendQueryParameters(call.request.queryParameters)

    val cachedResponseFile: String? = videoChunkCache.get(responseURI.toString())
    if (null != cachedResponseFile) {
        try {
            call.respondBytesWriter {
                File(cachedResponseFile).inputStream().use {
                    it.toByteReadChannel().copyAndClose(this)
                }
            }
        } catch (_: ChannelWriteException) {
            // Client closed connection
        }
        return
    }

    withUserPermit(user) {
        lateinit var preparedStatement: HttpStatement
        lateinit var newLocation: String

        channel.server.withConnection(
            channel.server.config.timeouts.totalMilliseconds,
        ) { connection, releaseConnectionEarly ->
            preparedStatement = connection.httpClient.prepareRequest {
                url(responseURI.toString())
                method = HttpMethod.Get
                headers {
                    filterAndAppendHttpRequestHeaders(this@headers, routingContext)
                    addDefaultClientHeaders(connection.config)
                    addHeadersFromPlaylistProps(channel.m3uProps, channel.vlcOpts)
                }
            }

            var redirects = 0

            do {
                preparedStatement.execute { response ->
                    newLocation = response.headers["Location"] ?: ""

                    if (newLocation.isNotBlank()) {
                        // Follow redirects
                        preparedStatement = connection.httpClient.prepareGet(newLocation) {
                            method = HttpMethod.Get
                            headers {
                                filterAndAppendHttpRequestHeaders(this@headers, routingContext)
                                addDefaultClientHeaders(connection.config)
                                addHeadersFromPlaylistProps(channel.m3uProps, channel.vlcOpts)
                            }
                        }

                        try {
                            responseURI = responseURI.resolve(newLocation)
                        } catch (_: URISyntaxException) {
                            LOG.warn("Invalid redirect URI found: $newLocation")
                            if (!config.sentry?.dsn.isNullOrBlank()) {
                                Sentry.captureMessage("Invalid redirect URI found: $newLocation")
                            }
                        }

                        redirects++
                    } else {
                        call.response.headers.apply {
                            response.headers.filterHttpResponseHeaders().forEach { key, value ->
                                value.forEach { append(key, it) }
                            }
                        }

                        try {
                            call.respondBytesWriter(
                                contentType = response.contentType(),
                                status = response.status,
                                contentLength = response.contentLength(),
                            ) {
                                val output = this

                                var totalCache = byteArrayOf()
                                val channel = response.bodyAsChannel()

                                // Keep reading and pushing to temp cache + output until exhausted
                                while (!channel.exhausted()) {
                                    val chunk = channel.readRemaining(131_072).readByteArray()
                                    if (totalCache.size < maxCacheableVideoChunkSize) totalCache += chunk
                                    output.writeFully(chunk)
                                }

                                // Immediately release the connection after the read channel is closed
                                releaseConnectionEarly()

                                // Flush any remaining data in the buffer
                                output.flush()

                                val remoteContentLength = response.contentLength() ?: 0L
                                if (
                                    totalCache.size < maxCacheableVideoChunkSize
                                    && "video/mp2t" == response.headers["Content-Type"]?.lowercase()
                                ) {
                                    // Cache the video chunk
                                    responseURI.let { responseURI -> totalCache.let { totalCache ->
                                        cacheCoroutineScope.launch { videoChunkCache.putAsync(responseURI.toString()) { fileName ->
                                            val file = File(fileName)
                                            file.writeBytes(totalCache)

                                            true
                                        } }
                                    } }
                                }
                            }
                        } catch (_: ChannelWriteException) {
                            // Client closed connection
                        }
                    }
                }
            } while (newLocation.isNotBlank() && redirects < maxRedirects)
        }
    }
}


