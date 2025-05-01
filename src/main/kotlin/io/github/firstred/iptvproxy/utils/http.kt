package io.github.firstred.iptvproxy.utils

import co.touchlab.stately.collections.ConcurrentMutableMap
import io.github.firstred.iptvproxy.config
import io.github.firstred.iptvproxy.dtos.config.IIptvServerConfigWithoutAccounts
import io.ktor.http.*
import io.ktor.server.routing.*
import io.ktor.util.*
import kotlinx.coroutines.sync.Mutex
import java.net.URI

fun Headers.filterAndAppendHttpRequestHeaders(
    whitelistedHeaders: List<String> = config.whitelistIptvClientHeaders,
    blacklistedHeaders: List<String> = config.blacklistIptvClientHeaders,
): Headers = filter { key, _ ->
    ((whitelistedHeaders + APP_REQUEST_HEADERS).any { it.equals(key, ignoreCase = true) } || whitelistedHeaders.contains("*"))
            && !(DROP_REQUEST_HEADERS + blacklistedHeaders - APP_REQUEST_HEADERS).any { it.equals(key, ignoreCase = true) }
}.toHeaders()

fun HeadersBuilder.filterAndAppendHttpRequestHeaders(
    builder: HeadersBuilder,
    context: RoutingContext,
    whitelistedHeaders: List<String> = config.whitelistIptvClientHeaders,
    blacklistedHeaders: List<String> = config.blacklistIptvClientHeaders,
) {
    (DROP_REQUEST_HEADERS - APP_REQUEST_HEADERS).forEach { builder.remove(it) }
    context.call.request.headers
        .filterAndAppendHttpRequestHeaders(whitelistedHeaders, blacklistedHeaders)
        .forEach { key, value -> value.forEach { builder.append(key, it) } }
}

fun Headers.filterHttpResponseHeaders(
    whitelistedHeaders: List<String> = config.whitelistIptvServerHeaders,
    blacklistedHeaders: List<String> = config.blacklistIptvServerHeaders,
): Headers = filter { key, _ ->
    ((whitelistedHeaders).any { it.equals(key, ignoreCase = true) } || whitelistedHeaders.contains("*"))
            && !(DROP_RESPONSE_HEADERS + blacklistedHeaders).any { it.equals(key, ignoreCase = true) }
}.toHeaders()

fun URI.appendQueryParameters(
    queryParameters: Parameters,
): URI {
    var newQueryString = query
    // Add all request parameters to the uri
    queryParameters.forEach { key, value ->
        newQueryString = "${query}${if (query.isNullOrEmpty()) "" else "&"}$key=$value"
    }
    return URI(scheme, authority, path, newQueryString, fragment)
}
private fun StringValues.toHeaders() = headers {
    forEach { key, values ->
        values.forEach { value -> append(key, value) }
    }
}

fun HeadersBuilder.forwardProxyUser(serverConfig: IIptvServerConfigWithoutAccounts) {
    if (!serverConfig.proxyStream) return
    serverConfig.proxyForwardedUser?.let { user ->
        append(HttpHeaders.Forwarded, "proxyUser=$user;pass=${serverConfig.proxyForwardedPassword}")
    }
}

fun HeadersBuilder.sendUserAgent(serverConfig: IIptvServerConfigWithoutAccounts) {
    serverConfig.userAgent?.let { userAgent ->
        if (userAgent.isBlank()) return

        remove(HttpHeaders.UserAgent)
        append(HttpHeaders.UserAgent, userAgent)
    }
}

private val urlRequestLocks = ConcurrentMutableMap<String, Mutex>()

suspend fun withRequestUrlPermit(
    url: String,
    action: suspend () -> Unit,
) {
    val lock = urlRequestLocks.getOrPut(url) { Mutex() }
    try {
        action()
    } finally {
        lock.unlock()
    }
}

