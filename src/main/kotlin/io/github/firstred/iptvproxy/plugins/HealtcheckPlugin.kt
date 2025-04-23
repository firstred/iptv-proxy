package io.github.firstred.iptvproxy.plugins

import io.github.firstred.iptvproxy.routes.healthcheck
import io.ktor.server.application.*
import io.ktor.server.routing.*

fun Application.installHealthCheckRoute() {
    routing {
        healthcheck()
    }
}
