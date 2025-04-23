package io.github.firstred.iptvproxy.plugins

import io.github.firstred.iptvproxy.monitors.HealthMonitor
import io.ktor.server.application.*
import org.koin.ktor.ext.get

fun Application.init() {
    get<HealthMonitor>()
}
