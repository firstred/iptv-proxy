package io.github.firstred.iptvproxy.utils

import io.github.firstred.iptvproxy.config
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.slf4j.LoggerFactory
import java.net.URI

private val LOG = LoggerFactory.getLogger("urlSignature")

fun String.pathSignature(): String = (this + config.appSecret).sha256()
fun URI.pathSignature(): String = path.pathSignature()

fun String.verifyPathSignature(): Boolean {
    val regex = Regex("""^/(?<before>.*)/(?<signature>[a-f0-9]+)/(?<after>[^/]*)$""")
    val regexResult = regex.find(this) ?: return false
    val (before, signature, after) = regexResult.destructured

    val calculated = "/$before/$after".pathSignature()

    if (calculated != signature) {
        LOG.warn("Invalid signature: $calculated != $signature, path used for calculation: /$before/$after")
        return false
    }

    return true
}
fun URI.verifyPathSignature(): Boolean = path.verifyPathSignature()

suspend fun RoutingContext.verifyPathSignature(overridePath: String? = null): Boolean {
    val path = overridePath ?: call.request.path()
    if (!path.verifyPathSignature()) {
        LOG.warn("Invalid signature for path: $path")
        call.respond(HttpStatusCode.Unauthorized, "Invalid signature for path")
        return false
    }

    return true
}
