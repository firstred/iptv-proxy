package io.github.firstred.iptvproxy.utils

import io.github.firstred.iptvproxy.routes.imagesRoute
import io.ktor.http.*
import sun.net.www.ParseUtil.toURI
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

fun String.isHlsPlaylist(): Boolean = Url(this).toEncodedJavaURI().isHlsPlaylist()
fun URI.isHlsPlaylist(): Boolean = path.lowercase().let { it.endsWith(".m3u") || it.endsWith(".m3u8") }

fun String.toProxiedIconUrl(baseUrl: URI, encryptedAccount: String) = toProxiedIconUrl(
    baseUrl.toString(),
    encryptedAccount
)
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

    return "${baseUrl}$imagesRoute/$encryptedAccount/${aesEncryptToHexString()}/${basename}.${extension}".replace(" ", "%20")
}

fun Url.toEncodedJavaURI() = URI(
    protocol.name,
    encodedUser,
    host,
    port,
    encodedPath,
    encodedQuery.ifBlank { null },
    encodedFragment.ifBlank { null },
)

@Suppress("HttpUrlsUsage")
fun String.hasSupportedScheme(): Boolean {
    val supportedSchemes = listOf("http://", "https://")
    return supportedSchemes.any { this.startsWith(it) }
}
