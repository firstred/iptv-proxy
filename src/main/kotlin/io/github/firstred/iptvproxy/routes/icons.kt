package io.github.firstred.iptvproxy.routes

import io.github.firstred.iptvproxy.plugins.findUserFromEncryptedAccountInRoutingContext
import io.github.firstred.iptvproxy.plugins.isNotMainEndpoint
import io.github.firstred.iptvproxy.utils.aesDecryptFromHexString
import io.github.firstred.iptvproxy.utils.appendQueryParameters
import io.github.firstred.iptvproxy.utils.filterHttpRequestHeaders
import io.github.firstred.iptvproxy.utils.filterHttpResponseHeaders
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*
import org.koin.core.qualifier.named
import org.koin.ktor.ext.inject
import java.net.URI

fun Route.icons() {
    val httpClient : HttpClient by inject(named("icon"))

    route("/icon/") {
        get(Regex("""(?<encryptedaccount>[0-9a-fA-F]+)/(?<encryptedremoteurl>[0-9a-fA-F]+)/(?<filename>((?<basename>[^.]+)\.(?<extension>.+)))""")) {
            if (isNotMainEndpoint()) return@get
            try {
                findUserFromEncryptedAccountInRoutingContext()
            } catch (_: Throwable) {
                call.respond(HttpStatusCode.Unauthorized, "Username and/or password incorrect")
                return@get
            }

            val encryptedRemoteUrl = call.parameters["encryptedremoteurl"]

            val uri = URI(encryptedRemoteUrl?.aesDecryptFromHexString() ?: throw IllegalArgumentException("Invalid remote url"))

            httpClient.request {
                url(uri.appendQueryParameters(call.request.queryParameters).toString())
                method = HttpMethod.Get
                headers {
                    filterHttpRequestHeaders(this@headers, this@get)
                }
            }.let { response ->
                call.response.headers.apply {
                    allValues().filterHttpResponseHeaders()
                }

                call.respondBytesWriter(response.contentType(), response.status, response.contentLength()) {
                    response.bodyAsChannel().copyAndClose(this)
                    flushAndClose()
                }
            }
        }
    }
}

