package io.github.firstred.iptvproxy.routes

import io.github.firstred.iptvproxy.plugins.proxyRemoteFile
import io.ktor.server.routing.*

fun Route.video() {
    route("/video/") { proxyRemoteFile() }
}


