package io.github.firstred.iptvproxy.routes

import io.github.firstred.iptvproxy.plugins.appMicrometerRegistry
import io.github.firstred.iptvproxy.plugins.isNotMetricsPort
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.cio.*

fun Routing.metrics() {
    get("/metrics") {
        if (isNotMetricsPort()) return@get

        try {
            call.respond(appMicrometerRegistry.scrape())
        } catch (_: ChannelWriteException) {
            // Client closed connection
        }
    }
}
