package io.github.firstred.iptvproxy.utils

import io.github.firstred.iptvproxy.config
import io.github.firstred.iptvproxy.dtos.config.IIptvServerConfigWithoutAccounts
import io.github.firstred.iptvproxy.dtos.config.IptvFlatServerConfig
import io.github.firstred.iptvproxy.dtos.config.IptvServerAccountConfig
import io.ktor.http.*
import io.ktor.server.routing.*
import io.ktor.util.*
import java.net.URI
import java.util.*
import kotlin.text.Charsets.UTF_8

fun Headers.filterHttpRequestHeaders(
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
        .filterHttpRequestHeaders(whitelistedHeaders, blacklistedHeaders)
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

fun HeadersBuilder.addDefaultClientHeaders(serverConfig: IptvFlatServerConfig) {
    forwardProxyUser(serverConfig)
    sendUserAgent(serverConfig)
    sendBasicAuth(serverConfig.account)
    addProxyAuthorizationHeaderIfNecessary()
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

fun HeadersBuilder.sendBasicAuth(accountConfig: IptvServerAccountConfig) {
    accountConfig.login?.let { login ->
        if (login.isBlank()) return
        val password = accountConfig.password ?: ""

        remove(HttpHeaders.Authorization)
        append(HttpHeaders.Authorization, "Basic ${Base64.getEncoder().encodeToString("$login:$password".toByteArray(UTF_8))}")
    }
}

fun HeadersBuilder.addProxyAuthorizationHeaderIfNecessary() {
    // Handle proxy authentication part of the configuration
    config.httpProxy?.let {
        var (_, _, _, username, password) = config.getActualHttpProxyConfiguration()!!

        // No authentication required -- continue
        if (username.isNullOrBlank() && password.isNullOrBlank()) return@let

        // Ensure proper formatting of username and password
        if (username.isNullOrBlank()) username = ""
        if (password.isNullOrBlank()) password = ""

        append(
            HttpHeaders.ProxyAuthorization,
            "Basic ${Base64.getEncoder().encodeToString("$username:$password".toByteArray(UTF_8))}",
        )
    }
}

