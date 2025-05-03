package io.github.firstred.iptvproxy.plugins

import io.github.firstred.iptvproxy.config
import io.github.firstred.iptvproxy.db.repositories.ChannelRepository
import io.github.firstred.iptvproxy.di.modules.IptvUsersByName
import io.github.firstred.iptvproxy.entities.IptvChannel
import io.github.firstred.iptvproxy.entities.IptvUser
import io.github.firstred.iptvproxy.listeners.HealthListener
import io.github.firstred.iptvproxy.managers.ChannelManager
import io.github.firstred.iptvproxy.managers.UserManager
import io.github.firstred.iptvproxy.routes.hls
import io.github.firstred.iptvproxy.routes.images
import io.github.firstred.iptvproxy.routes.notices
import io.github.firstred.iptvproxy.routes.xtreamApi
import io.github.firstred.iptvproxy.utils.addDefaultClientHeaders
import io.github.firstred.iptvproxy.utils.aesDecryptFromHexString
import io.github.firstred.iptvproxy.utils.appendQueryParameters
import io.github.firstred.iptvproxy.utils.filterAndAppendHttpRequestHeaders
import io.github.firstred.iptvproxy.utils.filterHttpRequestHeaders
import io.github.firstred.iptvproxy.utils.filterHttpResponseHeaders
import io.github.firstred.iptvproxy.utils.maxRedirects
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*
import io.sentry.Sentry
import org.koin.java.KoinJavaComponent.getKoin
import org.koin.ktor.ext.inject
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.URISyntaxException

private val LOG = LoggerFactory.getLogger("RoutingPlugin")

fun Application.configureRouting() {
    routing {
        images()
        xtreamApi()
        hls()

        notices()
    }
}

fun RoutingContext.isMainPort() = config.port == call.request.local.localPort
fun RoutingContext.isNotMainPort() = !isMainPort()
fun RoutingContext.isHealthcheckPort() = config.healthcheckPort == call.request.local.localPort
fun RoutingContext.isNotHealthcheckPort() = !isHealthcheckPort()
fun RoutingContext.isMetricsPort() = config.metricsPort == call.request.local.localPort
fun RoutingContext.isNotMetricsPort() = !isMetricsPort()

suspend fun RoutingContext.isReady(): Boolean {
    val health: HealthListener by getKoin().inject()

    if (!health.isReady()) {
        call.respond(HttpStatusCode.ServiceUnavailable, "Service is not ready")
        return false
    }

    return true
}

suspend fun RoutingContext.isNotReady() = !isReady()

suspend fun RoutingContext.isLive(): Boolean {
    val health: HealthListener by getKoin().inject()

    if (!health.isLive()) {
        call.respond(HttpStatusCode.ServiceUnavailable, "Service is not ready")
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
            call.respond(HttpStatusCode.Unauthorized, "Username and/or password incorrect")
            return@get
        }

        val channelId = (call.parameters["streamid"] ?: run {
            call.respond(HttpStatusCode.BadRequest, "Missing Stream ID")
            return@get
        }).toLong()

        withUserPermit(user) {
            call.response.headers.apply {
                append(HttpHeaders.ContentType, "audio/mpegurl")
                append(
                    HttpHeaders.ContentDisposition,
                    "attachment; filename=${call.request.path().substringAfterLast("/")}",
                )
            }

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
                })
        }
    }
}

fun Route.proxyRemoteVod() {
    val channelRepository: ChannelRepository by inject()

    get(Regex("""^(?<username>[^/]+)/(?<password>[^/]+)/(?<channelid>[^.]+?)(?:\.(?<extension>.*))?$""")) {
        if (isNotMainPort()) return@get
        if (isNotReady()) return@get
        val routingContext = this

        lateinit var user: IptvUser
        try {
            user = findUserFromUrlInRoutingContext()
        } catch (_: Throwable) {
            call.respond(HttpStatusCode.Unauthorized, "Username and/or password incorrect")
            return@get
        }

        val streamId = call.parameters["channelid"] ?: run {
            call.respond(HttpStatusCode.BadRequest, "Missing Stream ID")
            return@get
        }
        val channel = channelRepository.getChannelById(streamId.toLong()) ?: run {
            call.respond(HttpStatusCode.NotFound, "Channel not found")
            return@get
        }

        streamRemoteFile(user, channel, routingContext)
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
            call.respond(HttpStatusCode.BadRequest, "Invalid channel ID")
            return@get
        }
        val channel = channelRepository.getChannelById(channelId) ?: run {
            call.respond(HttpStatusCode.NotFound, "Channel not found")
            return@get
        }

        streamRemoteFile(
            user,
            channel,
            routingContext,
            (call.parameters["encryptedremoteurl"]?.let { URI(it.aesDecryptFromHexString()) }
                ?: throw IllegalArgumentException("Invalid remote URL"))
        )
    }
}

private suspend fun RoutingContext.streamRemoteFile(
    user: IptvUser,
    channel: IptvChannel,
    routingContext: RoutingContext,
    remoteUrl: URI = channel.url,
) {
    withUserPermit(user) {
        var responseURI = remoteUrl.appendQueryParameters(call.request.queryParameters)

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
                            }
                        }

                        try {
                            responseURI = responseURI.resolve(URI(newLocation))
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

                        call.respondBytesWriter(
                            contentType = response.contentType(),
                            status = response.status,
                            contentLength = response.contentLength(),
                        ) {
                            response.bodyAsChannel().copyAndClose(this)
                            // Immediately release the connection after the read channel is closed
                            releaseConnectionEarly()
                        }
                    }
                }
            } while (newLocation.isNotBlank() && redirects < maxRedirects)
        }
    }
}


