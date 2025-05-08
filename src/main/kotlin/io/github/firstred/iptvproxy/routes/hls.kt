package io.github.firstred.iptvproxy.routes

import io.github.firstred.iptvproxy.plugins.proxyRemoteHlsStream
import io.github.firstred.iptvproxy.plugins.proxyRemotePlaylist
import io.github.firstred.iptvproxy.plugins.proxyRemoteVideo
import io.ktor.server.routing.*

fun Route.hls() {
    // M3U8 live channel playlist before actual chunks are served
    route("/live/") { proxyRemotePlaylist() }
    route("/movie/") { proxyRemoteVideo() }
    route("/series/") { proxyRemoteVideo() }

    // Direct file access
    route("/hls/") { proxyRemoteHlsStream() }
}
