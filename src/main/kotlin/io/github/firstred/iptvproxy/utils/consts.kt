package io.github.firstred.iptvproxy.utils

// These are the headers that are used by the app and should not be removed
val APP_REQUEST_HEADERS = listOf(
    "x-iptv-proxy",        // Headers required for this app
    "proxy-authorization", // Headers required for this app
    "forwarded",           // Headers required for this app
)

val DROP_REQUEST_HEADERS = listOf(
    "host",                // Let KTOR client handle this
    "accept",              // Let KTOR client handle this
    "accept-charset",      // Let KTOR client handle this
    "accept-encoding",     // Let KTOR client handle this
    "transfer-encoding",   // Let KTOR client handle this

    "x-forwarded-for",     // Reserved for reverse proxies
    "x-forwarded-proto",   // Reserved for reverse proxies
    "x-forwarded-host",    // Reserved for reverse proxies
    "x-forwarded-port",    // Reserved for reverse proxies
    "x-forwarded-server",  // Reserved for reverse proxies
    "x-real-ip",           // Reserved for reverse proxies
    "x-remote-ip",         // Reserved for reverse proxies
    "x-remote-port",       // Reserved for reverse proxies
    "x-remote-addr",       // Reserved for reverse proxies
    "x-remote-user",       // Reserved for reverse proxies
    "x-remote-proto",      // Reserved for reverse proxies
    "x-remote-host",       // Reserved for reverse proxies
)

val DROP_RESPONSE_HEADERS = listOf(
    "content-encoding",   // Let KTOR server handle this
    "transfer-encoding",  // Let KTOR server handle this
    "connection",         // Let KTOR server handle this
    "origin",             // Let KTOR server handle this
    "vary",               // Let KTOR server handle this
    "content-length",     // Relay manually

    "access-control-allow-credentials",  // Should be set with CORS config
    "access-control-allow-origin",       // Should be set with CORS config
    "access-control-allow-headers",      // Should be set with CORS config
    "access-control-allow-methods",      // Should be set with CORS config
    "access-control-expose-headers",     // Should be set with CORS config
)

const val maxRedirects = 5 // HTTP 1.0 guidance recommends 5
