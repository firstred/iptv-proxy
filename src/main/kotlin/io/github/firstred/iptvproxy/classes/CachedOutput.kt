package io.github.firstred.iptvproxy.classes

import io.github.firstred.iptvproxy.enums.IptvChannelType
import io.github.firstred.iptvproxy.serialization.json
import kotlinx.serialization.builtins.serializer
import java.net.URI
import java.security.MessageDigest
import kotlin.time.Instant

enum class CachedOutputType(val containsBaseUrl: Boolean) {
    PLAYLIST(true),
    XMLTV(true),
    LIVE_STREAMS(true),
    MOVIES(true),
    SERIES(true),
    LIVE_CATEGORIES(false),
    MOVIE_CATEGORIES(false),
    SERIES_CATEGORIES(false),
}

/**
 * Describes a single cacheable output variant.
 *
 * Cached files are rendered with [PLACEHOLDER_BASE_URL] and the actual base URL of the client request is injected
 * while the file is streamed, which makes a cached file independent of the base URL used by the client.
 */
data class CachedOutput(
    val type: CachedOutputType,
    val user: IptvUser,
    val categoryId: UInt? = null,
    val includeTypes: Set<IptvChannelType>? = null,
    val excludeTypes: Set<IptvChannelType>? = null,
) {
    fun cacheKey(configFingerprint: String): String {
        val userFingerprint = sha256(
            encodeFields(
                listOf(
                    user.password,
                    user.maxConnections.toString(),
                    encodeFields(user.channelBlacklist.distinct().sorted()),
                    encodeFields(user.channelWhitelist.distinct().sorted()),
                    encodeFields(user.categoryBlacklist.distinct().sorted()),
                    encodeFields(user.categoryWhitelist.distinct().sorted()),
                    user.moviesEnabled.toString(),
                    user.seriesEnabled.toString(),
                )
            )
        )
        val normalizedParameters = encodeFields(
            listOf(
                categoryId?.toString() ?: "null",
                includeTypes?.let { "set:${encodeFields(it.map { type -> type.name }.distinct().sorted())}" } ?: "null",
                excludeTypes?.let { "set:${encodeFields(it.map { type -> type.name }.distinct().sorted())}" } ?: "null",
            )
        )
        val identity = encodeFields(
            listOf(
                type.name,
                user.username,
                userFingerprint,
                normalizedParameters,
                configFingerprint,
            )
        )

        return "${type.name.lowercase()}-${sha256(identity)}"
    }

    fun isStale(fileLastModifiedAtMillis: Long, lastDataUpdateAt: Instant?): Boolean =
        null != lastDataUpdateAt && fileLastModifiedAtMillis < lastDataUpdateAt.toEpochMilliseconds()

    /**
     * Encodes the base URL exactly as it appears inside the output format of this variant
     */
    fun encodeBaseUrl(baseUrl: URI): ByteArray = baseUrl.toString().let { value ->
        when (type) {
            CachedOutputType.PLAYLIST -> value
            CachedOutputType.XMLTV -> value.escapeXml()
            else -> json.encodeToString(String.serializer(), value).removePrefix("\"").removeSuffix("\"")
        }
    }.toByteArray(Charsets.UTF_8)

    private fun String.escapeXml(): String = buildString {
        this@escapeXml.forEach { char ->
            when (char) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&apos;")
                else -> append(char)
            }
        }
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    private fun encodeFields(values: List<String>): String = buildString {
        values.forEach { value ->
            append(value.length)
            append(':')
            append(value)
        }
    }

    companion object {
        /**
         * Base URL written into cached files; uses the reserved `.invalid` TLD and only characters that are never
         * escaped by the M3U, XML or JSON writers
         */
        val PLACEHOLDER_BASE_URL: URI = URI("http://iptv-proxy-base-url-placeholder.invalid/")
    }
}
