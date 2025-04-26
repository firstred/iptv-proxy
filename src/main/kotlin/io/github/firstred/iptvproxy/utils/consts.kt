package io.github.firstred.iptvproxy.utils

const val M3U_TAG_EXTINF = "#EXTINF:"
const val M3U_TAG_TARGET_DURATION = "#EXT-X-TARGETDURATION:"
const val EXT_X_MEDIA_SEQUENCE = "#EXT-X-MEDIA-SEQUENCE:"

val DROP_REQUEST_HEADERS = listOf(
    "host",
    "accept",
    "accept-encoding",
    "x-forwarded-for",
    "x-forwarded-proto",
    "x-forwarded-host",
    "x-forwarded-port",
    "x-real-ip",
    "x-remote-ip",
    "x-remote-port",
    "x-remote-addr",
    "x-remote-user",
    "x-remote-proto",
    "x-remote-host",
)

val DROP_RESPONSE_HEADERS = listOf(
    "content-encoding",
    "content-type",
    "content-length",
    "connection",
    "date",
    "access-control-allow-credentials",
    "access-control-allow-origin",
    "access-control-allow-headers",
    "access-control-allow-methods",
    "access-control-expose-headers",
    "origin",
    "vary",
    "x-memory",
    "x-route-time",
    "x-run-time",
)

const val maxRedirects = 6
