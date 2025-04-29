package io.github.firstred.iptvproxy.routes

import io.github.firstred.iptvproxy.config
import io.github.firstred.iptvproxy.entities.IptvUser
import io.github.firstred.iptvproxy.managers.ChannelManager
import io.github.firstred.iptvproxy.plugins.findUserFromEncryptedAccountInRoutingContext
import io.github.firstred.iptvproxy.plugins.isNotMainEndpoint
import io.github.firstred.iptvproxy.plugins.isNotReady
import io.github.firstred.iptvproxy.plugins.proxyRemoteFile
import io.github.firstred.iptvproxy.plugins.withUserPermit
import io.github.firstred.iptvproxy.utils.aesDecryptFromHexString
import io.github.firstred.iptvproxy.utils.appendQueryParameters
import io.github.firstred.iptvproxy.utils.filterHttpRequestHeaders
import io.github.firstred.iptvproxy.utils.filterHttpResponseHeaders
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*
import kotlinx.coroutines.sync.withLock
import org.koin.ktor.ext.inject
import java.net.URI

fun Route.hls() {
    get("/test") {
        call.respondText("test")
    }
//    val channelManager: ChannelManager by inject()
//    val channelsByReference: IptvChannelsByReference by inject()
//
//    // HLS references to channel, movie and series playlists
//    route("/hls/") {
//        route(Regex("""(?<encryptedaccount>[0-9a-fA-F]+)/(?<channelid>[^/]+)/(?<encryptedremoteurl>[0-9a-fA-F]+)/[^.]*\.ts""")) {
//            get {
//                if (isNotMainEndpoint()) return@get
//                try {
//                    findUserFromEncryptedAccountInRoutingContext()
//                } catch (_: Throwable) {
//                    call.respond(HttpStatusCode.Unauthorized, "Username and/or password incorrect")
//                    return@get
//                }
//
//                val channelId = call.parameters["channelid"] ?: ""
//                val remoteUrl = (call.parameters["encryptedremoteurl"] ?: "").aesDecryptFromHexString()
//
//                iptvChannelsLock.withLock {
//                    channelsByReference[channelId]!!.server.withConnection { connection ->
//                        connection.httpClient.request {
//                            url(URI(remoteUrl).appendQueryParameters(call.request.queryParameters).toString())
//                            method = HttpMethod.Get
//                            headers {
//                                filterHttpRequestHeaders(this@headers, this@get)
//                            }
//                        }.let { response ->
//                            call.response.headers.apply { allValues().filterHttpResponseHeaders() }
//
//                            call.respondBytesWriter(response.contentType(), response.status, response.contentLength()) {
//                                response.bodyAsChannel().copyAndClose(this)
//                                flushAndClose()
//                            }
//                        }
//                    }
//                }
//            }
//        }
//    }
//
//    // M3U8 live channel playlist before actual chunks are served
//    route("/live/") {
//        get(Regex("""(?<encryptedaccount>[0-9a-fA-F]+)/(?<channel>[a-z0-f]+)/[^.]*\.(?<extension>.*)$""")) {
//            if (isNotMainEndpoint()) return@get
//            if (isNotReady()) return@get
//
//            lateinit var user: IptvUser
//            try {
//                user = findUserFromEncryptedAccountInRoutingContext()
//            } catch (_: Throwable) {
//                call.respond(HttpStatusCode.Unauthorized, "Username and/or password incorrect")
//                return@get
//            }
//
//            val channelId = call.parameters["channel"] ?: ""
//
//            withUserPermit(user) {
//                call.response.headers.apply {
//                    append(HttpHeaders.ContentType, "audio/mpegurl")
//                    append(
//                        HttpHeaders.ContentDisposition,
//                        "attachment; filename=${call.request.path().substringAfterLast("/")}",
//                    )
//                }
//
//                call.respondText(
//                    channelManager.getChannelPlaylist(
//                        channelId,
//                        user,
//                        config.getActualBaseUrl(call.request),
//                        call.request.headers.filterHttpRequestHeaders(),
//                        call.request.queryParameters,
//                    ) { headers -> headers.filterHttpResponseHeaders().entries().forEach { (key, value) -> value.forEach { call.response.headers.append(key, it) } } })
//            }
//        }
//    }
//
//    // Direct file access
//    route("/movie/") { proxyRemoteFile() }
//    route("/series/") { proxyRemoteFile() }
//    route("/video/") { proxyRemoteFile() }
}


