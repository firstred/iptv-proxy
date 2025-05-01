package io.github.firstred.iptvproxy.utils

// These are the headers that are used by the app and should not be removed
val APP_REQUEST_HEADERS = listOf(
    "x-iptv-proxy",
    "proxy-authorization",
    "forwarded",
)

val DROP_REQUEST_HEADERS = listOf(
    "host",
    "accept",
    "accept-charset",
    "accept-encoding",
    "transfer-encoding",
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
    "transfer-encoding",
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

const val maxRedirects = 5 // HTTP 1.0 guidance recommends 5
