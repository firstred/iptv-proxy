package io.github.firstred.iptvproxy.routes

import io.github.firstred.iptvproxy.managers.ChannelManager
import io.github.firstred.iptvproxy.plugins.isNotAuthenticated
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
            val username = call.parameters["username"]
            val password = call.parameters["password"]

            if (isNotMainEndpoint()) return@get
            if (isNotAuthenticated(username, password = password)) return@get
            if (isNotReady()) return@get

            call.response.headers.apply {
                append(HttpHeaders.ContentType, "audio/mpegurl")
                append(HttpHeaders.ContentDisposition, "attachment; filename=playlist.m3u8")
            }

            call.respondOutputStream { use { output ->
                channelManager.getAllChannelsPlaylist(output, username!!)
            } }
        }
    }
}
