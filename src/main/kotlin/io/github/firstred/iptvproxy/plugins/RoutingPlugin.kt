package io.github.firstred.iptvproxy.plugins

import io.github.firstred.iptvproxy.config
import io.github.firstred.iptvproxy.db.repositories.ChannelRepository
import io.github.firstred.iptvproxy.di.modules.IptvUsersByName
import io.github.firstred.iptvproxy.entities.IptvUser
import io.github.firstred.iptvproxy.listeners.HealthListener
import io.github.firstred.iptvproxy.managers.ChannelManager
import io.github.firstred.iptvproxy.managers.UserManager
import io.github.firstred.iptvproxy.routes.hls
import io.github.firstred.iptvproxy.routes.icons
import io.github.firstred.iptvproxy.routes.notices
import io.github.firstred.iptvproxy.routes.xtreamApi
import io.github.firstred.iptvproxy.utils.aesDecryptFromHexString
import io.github.firstred.iptvproxy.utils.appendQueryParameters
import io.github.firstred.iptvproxy.utils.filterHttpRequestHeaders
import io.github.firstred.iptvproxy.utils.filterHttpResponseHeaders
import io.ktor.client.request.request
import io.ktor.client.request.url
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*
import org.koin.java.KoinJavaComponent.getKoin
import org.koin.ktor.ext.inject
import java.net.URI

fun Application.configureRouting() {
    routing {
        icons()
        xtreamApi()
        hls()

        notices()
    }
}

fun RoutingContext.isMainEndpoint() = config.port == call.request.local.localPort
fun RoutingContext.isNotMainEndpoint() = !isMainEndpoint()
fun RoutingContext.isHealthcheckEndpoint() = config.healthcheckPort == call.request.local.localPort
fun RoutingContext.isNotHealthcheckEndpoint() = !isHealthcheckEndpoint()
fun RoutingContext.isMetricsEndpoint() = config.metricsPort == call.request.local.localPort
fun RoutingContext.isNotMetricsEndpoint() = !isMetricsEndpoint()

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
    val proxyUsername = config.getForwardedValues(call.request.headers["Forwarded"]).proxyUser
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
        if (isNotMainEndpoint()) return@get
        if (isNotReady()) return@get

        lateinit var user: IptvUser
        try {
            user = findUserFromUrlInRoutingContext()
        } catch (_: Throwable) {
            call.respond(HttpStatusCode.Unauthorized, "Username and/or password incorrect")
            return@get
        }

        val channelId = call.parameters["streamid"] ?: run {
            call.respond(HttpStatusCode.BadRequest, "Missing Stream ID")
            return@get
        }

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

    get(Regex("""^(?<username>[^/]+)/(?<password>[^/]+)/(?<streamid>[^.]+)\.(?<extension>.*)$""")) {
        if (isNotMainEndpoint()) return@get
        if (isNotReady()) return@get

        lateinit var user: IptvUser
        try {
            user = findUserFromUrlInRoutingContext()
        } catch (_: Throwable) {
            call.respond(HttpStatusCode.Unauthorized, "Username and/or password incorrect")
            return@get
        }

        val streamId = call.parameters["streamid"] ?: run {
            call.respond(HttpStatusCode.BadRequest, "Missing Stream ID")
            return@get
        }
        val channel = channelRepository.getChannelById(streamId.toLong()) ?: run {
            call.respond(HttpStatusCode.NotFound, "Channel not found")
            return@get
        }

        // Direct video access requires an HTTP Range header to be set
        if (null == call.request.headers[HttpHeaders.Range]) {
            call.respond(HttpStatusCode.BadRequest, "Missing HTTP Range header")
            return@get
        }

        withUserPermit(user) {
            channel.server.withConnection { connection ->
                connection.httpClient.request {
                    url(channel.url.appendQueryParameters(call.request.queryParameters).toString())
                    method = HttpMethod.Get
                    headers {
                        filterHttpRequestHeaders(this@headers, this@get)
                    }
                }.let { response ->
                    call.response.headers.apply { allValues().filterHttpResponseHeaders() }

                    call.respondBytesWriter(response.contentType(), response.status, response.contentLength()) {
                        response.bodyAsChannel().copyAndClose(this)
                        flushAndClose()
                    }
                }
            }
        }
    }
}

fun Route.proxyRemoteHlsStream() {
    val channelRepository: ChannelRepository by inject()

    get(Regex("""^(?<encryptedaccount>[0-9a-fA-F]+)/(?<encryptedremoteurl>[0-9a-fA-F]+)/(?<channelid>[^/]+)/(?<filename>[^.]+)\.(?<extension>.*)$""")) {
        if (isNotMainEndpoint()) return@get
        findUserFromEncryptedAccountInRoutingContext()

        val channelId = (call.parameters["channelid"] ?: "").toLong()
        val remoteUrl = (call.parameters["encryptedremoteurl"] ?: "").aesDecryptFromHexString()
        if (channelId < 0) {
            call.respond(HttpStatusCode.BadRequest, "Invalid channel ID")
            return@get
        }
        val channel = channelRepository.getChannelById(channelId) ?: run {
            call.respond(HttpStatusCode.NotFound, "Channel not found")
            return@get
        }

        channel.server.withConnection { connection ->
            connection.httpClient.request {
                url(URI(remoteUrl).appendQueryParameters(call.request.queryParameters).toString())
                method = HttpMethod.Get
                headers {
                    filterHttpRequestHeaders(this@headers, this@get)
                }
            }.let { response ->
                call.response.headers.apply { allValues().filterHttpResponseHeaders() }

                call.respondBytesWriter(response.contentType(), response.status, response.contentLength()) {
                    response.bodyAsChannel().copyAndClose(this)
                    flushAndClose()
                }
            }
        }
    }
}


