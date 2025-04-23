package io.github.firstred.iptvproxy.routes

import io.github.firstred.iptvproxy.monitors.HealthMonitor
import io.github.firstred.iptvproxy.plugins.isNotHealthcheckEndpoint
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

fun Route.healthcheck() {
    val health: HealthMonitor by inject()

    route("/healthz/") {
            get("ready") {
                if (isNotHealthcheckEndpoint()) return@get

                handleReady(health)
            }
            get("live") {
                if (isNotHealthcheckEndpoint()) return@get

                handleLive(health)
            }
            get {
                if (isNotHealthcheckEndpoint()) return@get

                handleReady(health)
            }
    }
}

private suspend fun RoutingContext.handleLive(health: HealthMonitor) {
    if (health.isLive()) {
        call.respondText("OK")
    } else {
        call.respondText("NOT LIVE", status = HttpStatusCode.ServiceUnavailable)
    }
}

private suspend fun RoutingContext.handleReady(health: HealthMonitor) {
    if (health.isReady()) {
        call.respondText("OK")
    } else {
        call.respondText("NOT READY", status = HttpStatusCode.ServiceUnavailable)
    }
}
