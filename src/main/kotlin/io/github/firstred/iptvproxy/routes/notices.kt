package io.github.firstred.iptvproxy.routes

import io.ktor.server.routing.*

fun Route.notices() {
    route("/notices/") {
        get("hls_only") {
            hlsOnlyNoticeStream()
        }
    }
}
