package io.github.firstred.iptvproxy.routes

import io.github.firstred.iptvproxy.listeners.HealthListener
import io.github.firstred.iptvproxy.plugins.isNotHealthcheckPort
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.io.IOException
import org.koin.ktor.ext.inject

fun Route.healthcheck() {
    val health: HealthListener by inject()

    route("/healthz/") {
        get("ready") {
            if (isNotHealthcheckPort()) return@get

            handleReady(health)
        }
        get("live") {
            if (isNotHealthcheckPort()) return@get

            handleLive(health)
        }
        get {
            if (isNotHealthcheckPort()) return@get

            handleReady(health)
        }
    }
}

private suspend fun RoutingContext.handleLive(health: HealthListener) {
    try {
        if (health.isLive()) {
            call.respondText("OK")
        } else {
            call.respondText("NOT LIVE", status = HttpStatusCode.ServiceUnavailable)
        }
    } catch (_: IOException) {
        // Client closed connection
    }
}

private suspend fun RoutingContext.handleReady(health: HealthListener) {
    try {
        if (health.isReady()) {
            call.respondText("OK")
        } else {
            call.respondText("NOT READY", status = HttpStatusCode.ServiceUnavailable)
        }
    } catch (_: IOException) {
        // Client closed connection
    }
}
