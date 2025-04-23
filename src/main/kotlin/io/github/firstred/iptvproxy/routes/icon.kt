package io.github.firstred.iptvproxy.routes

import io.github.firstred.iptvproxy.plugins.isNotMainEndpoint
import io.github.firstred.iptvproxy.utils.base64.decodeBase64UrlString
import io.github.firstred.iptvproxy.utils.verifyPathSignature
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.cio.*
import io.ktor.utils.io.*
import org.koin.core.qualifier.named
import org.koin.ktor.ext.inject

fun Route.icon() {
    val httpClient : HttpClient by inject(named("icon"))

    route("/icon/") {
        get(Regex("""(?<base64remoteurl>[^/]+)/(?<signature>[^/]+)/(?<filename>((?<basename>[^.]+)\.(?<extension>.+)))""")) {
            if (isNotMainEndpoint()) return@get

            val base64RemoteUrl = call.parameters["base64remoteurl"]

            if (!verifyPathSignature()) return@get

            httpClient.request {
                url(base64RemoteUrl?.decodeBase64UrlString() ?: throw IllegalArgumentException("Invalid base64 remote url"))
                method = HttpMethod.Get
            }.let { response ->
                call.response.headers.apply {
                    response.contentLength()?.let { append(HttpHeaders.ContentLength, it.toString()) }
                    response.contentType()?.let { append(HttpHeaders.ContentType, it.toString()) }
                }

                call.respondBytesWriter { use {
                    response.bodyAsChannel().copyAndClose(this)
                } }
            }
        }
    }
}
