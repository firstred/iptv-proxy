package io.github.firstred.iptvproxy.plugins.ktor.server

import io.ktor.server.application.*
import io.ktor.server.application.hooks.*
import io.sentry.Sentry

val SentryPlugin = createApplicationPlugin(name = "SentryPlugin") {
    on(CallFailed) { _, cause -> Sentry.captureException(cause) }
}
