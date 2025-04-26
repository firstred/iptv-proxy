package io.github.firstred.iptvproxy.plugins

import io.github.firstred.iptvproxy.config
import io.github.firstred.iptvproxy.di.modules.IptvChannelsByReference
import io.github.firstred.iptvproxy.di.modules.IptvUsersByName
import io.github.firstred.iptvproxy.di.modules.iptvChannelsLock
import io.github.firstred.iptvproxy.entities.IptvUser
import io.github.firstred.iptvproxy.listeners.HealthListener
import io.github.firstred.iptvproxy.managers.UserManager
import io.github.firstred.iptvproxy.routes.hls
import io.github.firstred.iptvproxy.routes.icon
import io.github.firstred.iptvproxy.routes.live
import io.github.firstred.iptvproxy.routes.movie
import io.github.firstred.iptvproxy.routes.notices
import io.github.firstred.iptvproxy.routes.series
import io.github.firstred.iptvproxy.routes.video
import io.github.firstred.iptvproxy.routes.xtream
import io.github.firstred.iptvproxy.utils.aesDecryptFromHexString
import io.github.firstred.iptvproxy.utils.appendQueryParameters
import io.github.firstred.iptvproxy.utils.filterHttpRequestHeaders
import io.github.firstred.iptvproxy.utils.filterHttpResponseHeaders
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*
import kotlinx.coroutines.sync.withLock
import org.koin.java.KoinJavaComponent.getKoin
import org.koin.ktor.ext.inject
import java.net.URI

fun Application.configureRouting() {
    routing {
        xtream()
        icon()
        live()
        movie()
        series()
        hls()
        video()

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

fun Route.proxyRemoteFile() {
    val channelsByReference: IptvChannelsByReference by inject()

    get(Regex("""(?<encryptedaccount>[0-9a-fA-F]+)/(?<channelid>[^/]+)/(?<encryptedremoteurl>[0-9a-fA-F]+)/[^.]*\.ts""")) {
        if (isNotMainEndpoint()) return@get
        findUserFromEncryptedAccountInRoutingContext()

        val channelId = call.parameters["channelid"] ?: ""
        val remoteUrl = (call.parameters["encryptedremoteurl"] ?: "").aesDecryptFromHexString()

        iptvChannelsLock.withLock {
            channelsByReference[channelId]!!.server.withConnection { connection ->
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
}


