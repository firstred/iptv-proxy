package io.github.firstred.iptvproxy.routes

import io.github.firstred.iptvproxy.managers.ChannelManager
import io.github.firstred.iptvproxy.plugins.isNotAuthenticated
import io.github.firstred.iptvproxy.plugins.isNotMainEndpoint
import io.github.firstred.iptvproxy.plugins.isNotReady
import io.github.firstred.iptvproxy.plugins.withUserPermit
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

fun Route.live() {
    val channelManager: ChannelManager by inject()

    route("/live/") {
        get(Regex("""(?<username>[a-zA-Z0-9-]+)_(?<token>[a-f0-9]+)/(?<channel>[a-z0-f]+)/[^.]*\.m3u8?""")) {
            if (isNotMainEndpoint()) return@get

            val username = call.parameters["username"]
            val token = call.parameters["token"]
            val channelId = call.parameters["channel"] ?: ""

            if (isNotAuthenticated(username, token = token)) return@get
            if (isNotReady()) return@get

            withUserPermit(username!!) {
                call.response.headers.apply {
                    append(HttpHeaders.ContentType, "audio/mpegurl")
                    append(
                        HttpHeaders.ContentDisposition,
                        "attachment; filename=${call.request.path().substringAfterLast("/")}",
                    )
                }

                call.respondText(channelManager.getChannelPlaylist(channelId))
            }
        }
    }
}
