package io.github.firstred.iptvproxy.classes

import io.github.firstred.iptvproxy.enums.IptvChannelType
import io.github.firstred.iptvproxy.serialization.json
import kotlinx.serialization.builtins.serializer
import java.net.URI
import java.security.MessageDigest
import kotlin.time.Instant

enum class CachedOutputType(val containsPlaceholders: Boolean) {
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
 * User settings that change or filter the outputs, users with equal settings share their cached outputs.
 *
 * The filter lists are normalized, as neither their order nor duplicate entries affect the output.
 */
data class CachedOutputSettings(
    val channelBlacklist: List<String> = emptyList(),
    val channelWhitelist: List<String> = emptyList(),
    val categoryBlacklist: List<String> = emptyList(),
    val categoryWhitelist: List<String> = emptyList(),
    val moviesEnabled: Boolean = true,
    val seriesEnabled: Boolean = true,
) {
    /**
     * User the shared outputs are rendered for, its credentials are replaced with those of the requesting user
     */
    fun toPlaceholderUser() = IptvUser(
        username = CachedOutput.PLACEHOLDER_USERNAME,
        password = CachedOutput.PLACEHOLDER_PASSWORD,
        maxConnections = 1u,
        channelBlacklist = channelBlacklist,
        channelWhitelist = channelWhitelist,
        categoryBlacklist = categoryBlacklist,
        categoryWhitelist = categoryWhitelist,
        moviesEnabled = moviesEnabled,
        seriesEnabled = seriesEnabled,
    )
}

fun IptvUser.toCachedOutputSettings() = CachedOutputSettings(
    channelBlacklist = channelBlacklist.distinct().sorted(),
    channelWhitelist = channelWhitelist.distinct().sorted(),
    categoryBlacklist = categoryBlacklist.distinct().sorted(),
    categoryWhitelist = categoryWhitelist.distinct().sorted(),
    moviesEnabled = moviesEnabled,
    seriesEnabled = seriesEnabled,
)

/**
 * Describes a single cacheable output variant requested by [user].
 *
 * Cached files are shared by all users with the same [CachedOutputSettings]. They are rendered with
 * [PLACEHOLDER_BASE_URL], [PLACEHOLDER_USERNAME], [PLACEHOLDER_PASSWORD] and [PLACEHOLDER_ENCRYPTED_ACCOUNT], which are
 * replaced with the values of the actual request while the file is streamed.
 */
data class CachedOutput(
    val type: CachedOutputType,
    val user: IptvUser,
    val categoryId: UInt? = null,
    val includeTypes: Set<IptvChannelType>? = null,
    val excludeTypes: Set<IptvChannelType>? = null,
) {
    val settings: CachedOutputSettings
        get() = user.toCachedOutputSettings()

    fun cacheKey(configFingerprint: String): String {
        val settingsFingerprint = settings.let {
            sha256(
                encodeFields(
                    listOf(
                        encodeFields(it.channelBlacklist),
                        encodeFields(it.channelWhitelist),
                        encodeFields(it.categoryBlacklist),
                        encodeFields(it.categoryWhitelist),
                        it.moviesEnabled.toString(),
                        it.seriesEnabled.toString(),
                    )
                )
            )
        }
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
                settingsFingerprint,
                normalizedParameters,
                configFingerprint,
            )
        )

        return "${type.name.lowercase()}-${sha256(identity)}"
    }

    fun isStale(fileLastModifiedAtMillis: Long, lastDataUpdateAt: Instant?): Boolean =
        null != lastDataUpdateAt && fileLastModifiedAtMillis < lastDataUpdateAt.toEpochMilliseconds()

    /**
     * Maps the placeholders of a cached file to the values of the request of [user], encoded for the output format
     */
    fun placeholderReplacements(
        baseUrl: URI,
        encryptedAccount: String = user.toEncryptedAccountHexString(),
    ): List<Pair<ByteArray, ByteArray>> = if (type.containsPlaceholders) {
        listOf(
            PLACEHOLDER_BASE_URL.toString() to baseUrl.toString(),
            PLACEHOLDER_USERNAME to user.username,
            PLACEHOLDER_PASSWORD to user.password,
            PLACEHOLDER_ENCRYPTED_ACCOUNT to encryptedAccount,
        ).map { (placeholder, value) -> encodeValue(placeholder) to encodeValue(value) }
    } else emptyList()

    /**
     * Encodes the value exactly as it appears inside the output format of this variant
     */
    fun encodeValue(value: String): ByteArray = when (type) {
        CachedOutputType.PLAYLIST -> value
        CachedOutputType.XMLTV -> value.escapeXml()
        else -> json.encodeToString(String.serializer(), value).removePrefix("\"").removeSuffix("\"")
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
        /*
         * Placeholders written into cached files; they use the reserved `.invalid` TLD or a distinctive name and only
         * characters that are never escaped by the M3U, XML, JSON or URL writers
         */
        val PLACEHOLDER_BASE_URL: URI = URI("http://iptv-proxy-base-url-placeholder.invalid/")
        const val PLACEHOLDER_USERNAME = "iptv-proxy-username-placeholder"
        const val PLACEHOLDER_PASSWORD = "iptv-proxy-password-placeholder"
        const val PLACEHOLDER_ENCRYPTED_ACCOUNT = "iptv-proxy-encrypted-account-placeholder"
    }
}
