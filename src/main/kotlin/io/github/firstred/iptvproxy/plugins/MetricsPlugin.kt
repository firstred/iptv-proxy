package io.github.firstred.iptvproxy.plugins

import io.github.firstred.iptvproxy.routes.metrics
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry

val appMicrometerRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)

fun Application.installMetricsRoute() {
    routing {
        metrics()
    }
}
