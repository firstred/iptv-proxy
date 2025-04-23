package io.github.firstred.iptvproxy.routes

import io.github.firstred.iptvproxy.plugins.isNotAuthenticated
import io.github.firstred.iptvproxy.plugins.isNotMainEndpoint
import io.github.firstred.iptvproxy.utils.base64.decodeBase64UrlString
import io.github.firstred.iptvproxy.utils.verifyPathSignature
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*
import org.koin.core.qualifier.named
import org.koin.ktor.ext.inject

@OptIn(InternalAPI::class)
fun Route.icon() {
    val httpClient : HttpClient by inject(named("icon"))

    route("/icon/") {
        get(Regex("""(?<username>[a-zA-Z0-9-]+)_(?<token>[^/]+)/(?<base64remoteurl>[^/]+)/(?<signature>[^/]+)/(?<filename>((?<basename>[^.]+)\.(?<extension>.+)))""")) {
            if (isNotMainEndpoint()) return@get

            val username = call.parameters["username"]
            val token = call.parameters["token"]
            val base64RemoteUrl = call.parameters["base64remoteurl"]
            val signature = call.parameters["signature"] ?: ""

            if (isNotAuthenticated(username, token = token)) return@get
            if (!verifyPathSignature(signature)) return@get

            // By: LTFan
            // https://medium.com/@xfqwdsj/using-ktor-to-reverse-proxy-keycloak-485915fafd1e
            httpClient.request {
                url(base64RemoteUrl?.decodeBase64UrlString() ?: throw IllegalArgumentException("Invalid base64 remote url"))
                method = HttpMethod.Get
                headers.appendAll(call.request.headers)
            }.let { response ->
                call.respond(
                    object : OutgoingContent.WriteChannelContent() {
                        override val contentLength: Long? = response.contentLength()
                        override val contentType: ContentType? = response.contentType()
                        override val status: HttpStatusCode = response.status
                        override val headers: Headers = Headers.build { appendAll(response.headers) }

                        override suspend fun writeTo(channel: ByteWriteChannel) {
                            response.rawContent.copyAndClose(channel)
                        }
                    },
                    typeInfo = null,
                )
            }
        }
    }
}
