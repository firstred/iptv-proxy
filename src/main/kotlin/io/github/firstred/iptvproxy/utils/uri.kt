package io.github.firstred.iptvproxy.utils

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import kotlin.text.Charsets.UTF_8

fun URI.ensureTrailingSlash(): URI = let {
    // Ensure trailing slash
    if (!it.path.endsWith("/")) {
        it.resolve("${it.path}/")
    } else {
        it
    }
}

fun URI.isHlsPlaylist(): Boolean = path.endsWith(".m3u") || path.endsWith(".m3u8")

fun String.toProxiedIconUrl(baseUrl: String, encryptedAccount: String): String {
    val regex = Regex("""^.+/(?<filename>((?<basename>[^.]*)\.(?<extension>.*)))$""")
    val matchResult = regex.find(this)
    val basename = URLEncoder.encode(
        URLDecoder.decode(
            matchResult?.groups?.get("basename")?.value ?: "logo", UTF_8.toString()
        ).filterNot { it.isWhitespace() }, UTF_8.toString()
    )
    val extension = URLEncoder.encode(
        URLDecoder.decode(
            matchResult?.groups?.get("extension")?.value ?: "png", UTF_8.toString()
        ).filterNot { it.isWhitespace() }, UTF_8.toString()
    )

    return "${baseUrl}icon/$encryptedAccount/${aesEncryptToHexString()}/${basename}.${extension}"
}
