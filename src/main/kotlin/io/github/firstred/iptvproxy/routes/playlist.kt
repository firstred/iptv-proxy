package io.github.firstred.iptvproxy.routes

import io.github.firstred.iptvproxy.config
import io.github.firstred.iptvproxy.managers.ChannelManager
import io.github.firstred.iptvproxy.plugins.findUserFromRoutingContext
import io.github.firstred.iptvproxy.plugins.isNotMainEndpoint
import io.github.firstred.iptvproxy.plugins.isNotReady
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

fun Route.playlist() {
    val channelManager: ChannelManager by inject()

    route("/playlist/") {
        get(Regex("""(?<username>[a-zA-Z0-9-]+)_(?<password>[^/]+)""")) {
            if (isNotMainEndpoint()) return@get
            if (isNotReady()) return@get

            val user = findUserFromRoutingContext()

            call.response.headers.apply {
                append(HttpHeaders.ContentType, "audio/mpegurl")
                append(HttpHeaders.ContentDisposition, "attachment; filename=playlist.m3u8")
            }

            call.respondOutputStream { use { output ->
                channelManager.getAllChannelsPlaylist(
                    output,
                    user,
                    config.getActualBaseUrl(call.request),
                )
            } }
        }
    }
}
