package io.github.firstred.iptvproxy.utils

import java.net.URI

fun URI.ensureTrailingSlash(): URI = let {
    // Ensure trailing slash
    if (!it.path.endsWith("/")) {
        it.resolve("${it.path}/")
    } else {
        it
    }
}

fun URI.isHlsPlaylist(): Boolean = path.endsWith(".m3u") || path.endsWith(".m3u8")
