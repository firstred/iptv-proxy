package io.github.firstred.iptvproxy.routes

import io.github.firstred.iptvproxy.plugins.appMicrometerRegistry
import io.github.firstred.iptvproxy.plugins.isNotMetricsEndpoint
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Routing.metrics() {
    get("/metrics") {
        if (isNotMetricsEndpoint()) return@get

        call.respond(appMicrometerRegistry.scrape())
    }
}
