package io.github.firstred.iptvproxy.routes

import io.ktor.server.routing.*

fun Route.notices() {
    route("/notices/hls_only") {
        hlsOnlyNoticeStream()
    }
}
