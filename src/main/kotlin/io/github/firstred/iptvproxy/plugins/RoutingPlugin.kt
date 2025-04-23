package io.github.firstred.iptvproxy.plugins

import io.github.firstred.iptvproxy.config
import io.github.firstred.iptvproxy.listeners.HealthListener
import io.github.firstred.iptvproxy.routes.epg
import io.github.firstred.iptvproxy.routes.hls
import io.github.firstred.iptvproxy.routes.icon
import io.github.firstred.iptvproxy.routes.live
import io.github.firstred.iptvproxy.routes.notices
import io.github.firstred.iptvproxy.routes.playlist
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.core.context.GlobalContext.get

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
    val health: HealthListener = get().get()

    if (!health.isReady()) {
        call.respond(HttpStatusCode.ServiceUnavailable, "Service is not ready")
        return false
    }

    return true
}
suspend fun RoutingContext.isNotReady() = !isReady()

suspend fun RoutingContext.isLive(): Boolean {
    val health: HealthListener = get().get()

    if (!health.isLive()) {
        call.respond(HttpStatusCode.ServiceUnavailable, "Service is not ready")
        return false
    }

    return true
}
suspend fun RoutingContext.isNotLive() = !isLive()
