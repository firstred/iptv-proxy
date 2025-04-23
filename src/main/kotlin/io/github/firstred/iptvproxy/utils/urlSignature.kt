package io.github.firstred.iptvproxy.utils

import io.github.firstred.iptvproxy.config
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.net.URI
import java.util.*
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

fun String.pathSignature(): String {
    Base64.getEncoder()
    var keyString = config.appSecret
    val resource = this
    keyString = keyString.replace('-', '+')
    keyString = keyString.replace('_', '/')
    val key = Base64.getDecoder().decode(keyString)
    val sha256Key = SecretKeySpec(key, "HmacSHA256")
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(sha256Key)

    // compute the binary signature for the request
    val sigBytes = mac.doFinal(resource.toByteArray())
    var signature = Base64.getEncoder().encodeToString(sigBytes)

    // convert the signature to 'web safe' base 64
    signature = signature.replace('+', '-')
    signature = signature.replace('/', '_')

    return signature
}
fun URI.pathSignature(): String = path.pathSignature()

fun String.verifyPathSignature(
    signature: String,
): Boolean = pathSignature() == signature
suspend fun RoutingContext.verifyPathSignature(signature: String): Boolean {
    if (!call.request.path().verifyPathSignature(signature)) {
        call.respond(HttpStatusCode.Unauthorized, "Invalid signature for path")
        return false
    }

    return true
}
