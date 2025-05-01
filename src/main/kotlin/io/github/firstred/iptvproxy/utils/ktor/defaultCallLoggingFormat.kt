package io.github.firstred.iptvproxy.utils.ktor

import io.github.firstred.iptvproxy.utils.toHumanReadableSize
import io.ktor.server.plugins.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.request.*

fun CallLoggingConfig.defaultCallLoggingFormat() {
    // by Mehedi Hassan Piash
    // https://piashcse.medium.com/enhanced-logging-in-ktor-server-with-calllogging-feature-17729e931896
    filter { call -> call.request.path().startsWith("/") }
    format { call ->
        val status = call.response.status()
        val httpMethod = call.request.httpMethod.value
        val path = call.request.path()
        val queryParams =
            call.request.queryParameters
                .entries()
                .joinToString(", ") { "${it.key}=${it.value}" }
        val requestHeaders =
            call.request.headers
                .entries()
                .joinToString(", ") { "${it.key}=${it.value}" }
        val responseHeaders = call.response.headers.allValues().entries()
            .joinToString(", ") { "${it.key}=${it.value}" }
        val duration = call.processingTimeMillis()
        val remoteHost = call.request.origin.remoteHost
        val responseSize = (call.response.headers["Content-Length"] ?: "0").toHumanReadableSize()
        val coloredStatus =
            when {
                status == null -> "\u001B[33mUNKNOWN\u001B[0m"
                status.value < 300 -> "\u001B[32m$status\u001B[0m"
                status.value < 400 -> "\u001B[33m$status\u001B[0m"
                else -> "\u001B[31m$status\u001B[0m"
            }
        val coloredMethod = "\u001B[36m$httpMethod\u001B[0m"
        """
|------------------------ Incoming Request ------------------------
| Status: $coloredStatus
| Method: $coloredMethod
| Client IP/Host: $remoteHost
| Path: $path
| Query Params: $queryParams
| Request Headers: $requestHeaders
| Response Headers: $responseHeaders
| Response Size: $responseSize
| Duration: ${duration}ms
|------------------------------------------------------------------"""
    }
    // endby
}
