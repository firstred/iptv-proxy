package io.github.firstred.iptvproxy.utils

const val TAG_EXTINF = "#EXTINF:"
const val TAG_TARGET_DURATION = "#EXT-X-TARGETDURATION:"

val PROXIES_HEADERS: Set<String> =
    setOf(
        "content-type",
        "content-length",
        "connection",
        "date",
        "access-control-allow-origin",
        "access-control-allow-headers",
        "access-control-allow-methods",
        "access-control-expose-headers",
        "x-memory",
        "x-route-time",
        "x-run-time",
    )
