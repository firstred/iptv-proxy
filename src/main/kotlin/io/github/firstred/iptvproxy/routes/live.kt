package io.github.firstred.iptvproxy.routes

import io.github.firstred.iptvproxy.config
import io.github.firstred.iptvproxy.managers.ChannelManager
import io.github.firstred.iptvproxy.plugins.findUserFromRoutingContext
import io.github.firstred.iptvproxy.plugins.isNotMainEndpoint
import io.github.firstred.iptvproxy.plugins.isNotReady
import io.github.firstred.iptvproxy.plugins.withUserPermit
import io.github.firstred.iptvproxy.utils.filterHttpRequestHeaders
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

fun Route.live() {
    val channelManager: ChannelManager by inject()

    route("/live/") {
        get(Regex("""(?<encryptedaccount>[0-9a-fA-F]+)/(?<channel>[a-z0-f]+)/[^.]*\.m3u8?""")) {
            if (isNotMainEndpoint()) return@get
            if (isNotReady()) return@get
            val user = findUserFromRoutingContext()

            val channelId = call.parameters["channel"] ?: ""

            withUserPermit(user) {
                call.response.headers.apply {
                    append(HttpHeaders.ContentType, "audio/mpegurl")
                    append(
                        HttpHeaders.ContentDisposition,
                        "attachment; filename=${call.request.path().substringAfterLast("/")}",
                    )
                }

                call.respondText(
                    channelManager.getChannelPlaylist(
                    channelId,
                    user,
                    config.getActualBaseUrl(call.request),
                    call.request.headers.filterHttpRequestHeaders(),
                    call.request.queryParameters,
                    ) { headers -> headers.entries().forEach { (key, value) -> value.forEach { call.response.headers.append(key, it) } } })
            }
        }
    }
}
