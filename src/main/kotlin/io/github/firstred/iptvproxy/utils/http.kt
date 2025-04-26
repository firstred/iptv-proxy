package io.github.firstred.iptvproxy.utils

import io.github.firstred.iptvproxy.config
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.*
import java.net.URI

fun Headers.filterHttpRequestHeaders(
    whitelistedHeaders: List<String> = config.whitelistIptvClientHeaders,
    blacklistedHeaders: List<String> = config.blacklistIptvClientHeaders,
): Headers = filter { key, _ ->
    (whitelistedHeaders.any { it.equals(key, ignoreCase = true) } || whitelistedHeaders.contains("*"))
            && !(DROP_REQUEST_HEADERS + blacklistedHeaders).any { it.equals(key, ignoreCase = true) }
}.toHeaders()

fun HeadersBuilder.filterHttpRequestHeaders(
    builder: HeadersBuilder,
    context: RoutingContext,
    whitelistedHeaders: List<String> = config.whitelistIptvClientHeaders,
    blacklistedHeaders: List<String> = config.blacklistIptvClientHeaders,
) {
    DROP_REQUEST_HEADERS.forEach { builder.remove(it) }
    context.call.request.headers
        .filterHttpRequestHeaders(whitelistedHeaders, blacklistedHeaders)
        .forEach { key, value -> value.forEach { builder.append(key, it) } }
}

fun Headers.filterHttpResponseHeaders(
    whitelistedHeaders: List<String> = config.whitelistIptvServerHeaders,
    blacklistedHeaders: List<String> = config.blacklistIptvServerHeaders,
): Headers = filter { key, _ ->
    (whitelistedHeaders.any { it.equals(key, ignoreCase = true) } || whitelistedHeaders.contains("*"))
            && !(DROP_RESPONSE_HEADERS + blacklistedHeaders).any { it.equals(key, ignoreCase = true) }
}.toHeaders()

fun ResponseHeaders.filterHttpResponseHeaders(
    response: HttpResponse,
    whitelistedHeaders: List<String> = config.whitelistIptvServerHeaders,
    blacklistedHeaders: List<String> = config.blacklistIptvServerHeaders,
) {
    response.headers
        .filterHttpRequestHeaders(whitelistedHeaders, blacklistedHeaders)
        .forEach { key, value -> value.forEach { append(key, it) } }
}

fun appendQueryParameters(
    url: URI,
    queryParameters: Parameters,
): URI {
    var newQueryString = url.query
    // Add all request parameters to the uri
    queryParameters.forEach { key, value ->
        newQueryString = "${url.query}${if (url.query.isNullOrEmpty()) "" else "&"}$key=$value"
    }
    return URI(url.scheme, url.authority, url.path, newQueryString, url.fragment)
}

private fun StringValues.toHeaders() = headers {
    forEach { key, values ->
        values.forEach { value -> append(key, value) }
    }
}
