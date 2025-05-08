package io.github.firstred.iptvproxy.routes

import io.github.firstred.iptvproxy.plugins.findUserFromEncryptedAccountInRoutingContext
import io.github.firstred.iptvproxy.plugins.isNotMainPort
import io.github.firstred.iptvproxy.utils.aesDecryptFromHexString
import io.github.firstred.iptvproxy.utils.appendQueryParameters
import io.github.firstred.iptvproxy.utils.filterAndAppendHttpRequestHeaders
import io.github.firstred.iptvproxy.utils.filterHttpResponseHeaders
import io.github.firstred.iptvproxy.utils.hasSupportedScheme
import io.github.firstred.iptvproxy.utils.toEncodedJavaURI
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*
import org.koin.core.qualifier.named
import org.koin.ktor.ext.inject

const val imagesRoute = "images"

fun Route.images() {
    val httpClient : HttpClient by inject(named("images"))

    route("/$imagesRoute/") {
        get(Regex("""^(?<encryptedaccount>[0-9a-fA-F]+)/(?<encryptedremoteurl>[0-9a-fA-F]+)/(?<filename>((?<basename>[^.]+)\.(?<extension>.+)))$""")) {
            if (isNotMainPort()) return@get
            try {
                findUserFromEncryptedAccountInRoutingContext()
            } catch (_: Throwable) {
                call.respond(HttpStatusCode.Unauthorized, "Username and/or password incorrect")
                return@get
            }

            val remoteUrl = call.parameters["encryptedremoteurl"]?.aesDecryptFromHexString()
                ?: throw IllegalArgumentException("Invalid remote url")

            if (!remoteUrl.hasSupportedScheme()) {
                LOG.warn("Remote url must start with http(s):// - actual: {}", remoteUrl)

                call.respond(HttpStatusCode.NotFound)
                return@get
            }

            val uri = Url(remoteUrl).toEncodedJavaURI()

            httpClient.prepareRequest {
                url(uri.appendQueryParameters(call.request.queryParameters).toString())
                method = HttpMethod.Get
                headers {
                    filterAndAppendHttpRequestHeaders(this@headers, this@get)
                }
            }.execute { response ->
                call.response.headers.apply {
                    response.headers.filterHttpResponseHeaders().forEach { key, value ->
                        value.forEach { append(key, it) }
                    }
                }

                call.respondBytesWriter(response.contentType(), response.status, response.contentLength()) {
                    response.bodyAsChannel().copyAndClose(this)
                    flushAndClose()
                }
            }
        }
    }
}

