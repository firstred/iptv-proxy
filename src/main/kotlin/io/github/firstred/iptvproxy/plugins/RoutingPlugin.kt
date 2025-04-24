package io.github.firstred.iptvproxy.plugins

import io.github.firstred.iptvproxy.config
import io.github.firstred.iptvproxy.di.modules.IptvUsersByName
import io.github.firstred.iptvproxy.entities.IptvUser
import io.github.firstred.iptvproxy.listeners.HealthListener
import io.github.firstred.iptvproxy.managers.UserManager
import io.github.firstred.iptvproxy.routes.epg
import io.github.firstred.iptvproxy.routes.hls
import io.github.firstred.iptvproxy.routes.icon
import io.github.firstred.iptvproxy.routes.live
import io.github.firstred.iptvproxy.routes.notices
import io.github.firstred.iptvproxy.routes.playlist
import io.github.firstred.iptvproxy.utils.aesDecryptFromHexString
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.java.KoinJavaComponent.getKoin

fun Application.configureRouting() {
    routing {
        playlist()
        epg()
        icon()
        live()
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

fun RoutingContext.findUserFromRoutingContext(): IptvUser {
    val userManager: UserManager by getKoin().inject()

    val encryptedAccount = call.parameters["encryptedaccount"]
    encryptedAccount?.aesDecryptFromHexString()?.let { decrypted ->
        val username = decrypted.substringBefore('_')
        val password = decrypted.substringAfter('_')

        return userManager.getUser(username, password)
    }

    val proxyUsername = config.getForwardedValues(call.request.headers["Forwarded"]).proxyUser
    val username = proxyUsername ?: call.parameters["username"]
    val password = proxyUsername?.let { getKoin().get<IptvUsersByName>()[it]?.password } ?: call.parameters["password"]

    return userManager.getUser(username.toString(), password.toString())
}
