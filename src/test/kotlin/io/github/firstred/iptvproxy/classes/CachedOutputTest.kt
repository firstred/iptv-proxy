package io.github.firstred.iptvproxy.classes

import io.github.firstred.iptvproxy.enums.IptvChannelType
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

class CachedOutputTest {
    private val baseOutput = CachedOutput(
        type = CachedOutputType.PLAYLIST,
        user = user(),
    )

    @Test
    fun `cache key is stable for the same input`() {
        assertEquals(baseOutput.cacheKey("config"), baseOutput.cacheKey("config"))
        assertNotEquals(baseOutput.cacheKey("config"), baseOutput.cacheKey("different-config"))
    }

    @Test
    fun `users with the same settings share the cache key`() {
        val first = baseOutput.copy(
            user = user(
                username = "first",
                password = "first-secret",
                maxConnections = 1u,
                channelBlacklist = listOf("B", "A", "A"),
                categoryWhitelist = listOf("News", "Sports"),
            )
        )
        val second = baseOutput.copy(
            user = user(
                username = "second",
                password = "second-secret",
                maxConnections = 5u,
                channelBlacklist = listOf("A", "B"),
                categoryWhitelist = listOf("Sports", "News"),
            )
        )

        assertEquals(first.settings, second.settings)
        assertEquals(first.cacheKey("config"), second.cacheKey("config"))
    }

    @Test
    fun `cache key differs for settings that change or filter the output`() {
        listOf(
            user(channelBlacklist = listOf("Blocked")),
            user(channelWhitelist = listOf("Allowed")),
            user(categoryBlacklist = listOf("Blocked")),
            user(categoryWhitelist = listOf("Allowed")),
            user(moviesEnabled = false),
            user(seriesEnabled = false),
        ).forEach { user ->
            assertNotEquals(baseOutput.settings, baseOutput.copy(user = user).settings)
            assertNotEquals(baseOutput.cacheKey("config"), baseOutput.copy(user = user).cacheKey("config"))
        }

        // The same filter in a different list is a different setting
        assertNotEquals(
            baseOutput.copy(user = user(channelBlacklist = listOf("Name"))).cacheKey("config"),
            baseOutput.copy(user = user(categoryBlacklist = listOf("Name"))).cacheKey("config"),
        )
    }

    @Test
    fun `cache key differs for request parameters`() {
        assertNotEquals(baseOutput.cacheKey("config"), baseOutput.copy(type = CachedOutputType.XMLTV).cacheKey("config"))
        assertNotEquals(baseOutput.cacheKey("config"), baseOutput.copy(categoryId = 7u).cacheKey("config"))
        assertNotEquals(
            baseOutput.cacheKey("config"),
            baseOutput.copy(includeTypes = setOf(IptvChannelType.live)).cacheKey("config"),
        )
        assertNotEquals(
            baseOutput.cacheKey("config"),
            baseOutput.copy(excludeTypes = setOf(IptvChannelType.movie)).cacheKey("config"),
        )
    }

    @Test
    fun `include and exclude order is normalized`() {
        val first = baseOutput.copy(
            includeTypes = linkedSetOf(IptvChannelType.live, IptvChannelType.movie),
            excludeTypes = linkedSetOf(IptvChannelType.series, IptvChannelType.radio_streams),
        )
        val second = baseOutput.copy(
            includeTypes = linkedSetOf(IptvChannelType.movie, IptvChannelType.live),
            excludeTypes = linkedSetOf(IptvChannelType.radio_streams, IptvChannelType.series),
        )

        assertEquals(first.cacheKey("config"), second.cacheKey("config"))
    }

    @Test
    fun `cache key does not contain user credentials`() {
        assertFalse(baseOutput.cacheKey("config").contains(baseOutput.user.username))
        assertFalse(baseOutput.cacheKey("config").contains(baseOutput.user.password))
    }

    @Test
    fun `placeholder user keeps the settings without the credentials`() {
        val settings = user(channelWhitelist = listOf("Allowed"), seriesEnabled = false).toCachedOutputSettings()
        val placeholderUser = settings.toPlaceholderUser()

        assertEquals(CachedOutput.PLACEHOLDER_USERNAME, placeholderUser.username)
        assertEquals(CachedOutput.PLACEHOLDER_PASSWORD, placeholderUser.password)
        assertEquals(settings, placeholderUser.toCachedOutputSettings())
    }

    @Test
    fun `values are encoded for the output format`() {
        val value = "https://iptv.example/path/?a=1&b='2'"

        assertEquals("https://iptv.example/path/?a=1&b='2'", baseOutput.encodeValue(value).decodeToString())
        assertEquals(
            "https://iptv.example/path/?a=1&amp;b=&apos;2&apos;",
            baseOutput.copy(type = CachedOutputType.XMLTV).encodeValue(value).decodeToString(),
        )
        assertEquals(
            "https://iptv.example/path/?a=1&b='2'",
            baseOutput.copy(type = CachedOutputType.LIVE_STREAMS).encodeValue(value).decodeToString(),
        )
        assertEquals(
            "a\\\"b",
            baseOutput.copy(type = CachedOutputType.LIVE_STREAMS).encodeValue("a\"b").decodeToString(),
        )
    }

    @Test
    fun `placeholders are identical in every output format`() {
        val placeholders = listOf(
            CachedOutput.PLACEHOLDER_BASE_URL.toString(),
            CachedOutput.PLACEHOLDER_USERNAME,
            CachedOutput.PLACEHOLDER_PASSWORD,
            CachedOutput.PLACEHOLDER_ENCRYPTED_ACCOUNT,
        )

        CachedOutputType.entries.forEach { type ->
            placeholders.forEach { placeholder ->
                assertEquals(placeholder, baseOutput.copy(type = type).encodeValue(placeholder).decodeToString())
            }
        }
    }

    @Test
    fun `placeholder replacements use the values of the requesting user`() {
        val baseUrl = URI("https://iptv.example/")
        val replacements = baseOutput.placeholderReplacements(baseUrl, encryptedAccount = "0123abcd")
            .map { (placeholder, value) -> placeholder.decodeToString() to value.decodeToString() }

        assertEquals(
            listOf(
                CachedOutput.PLACEHOLDER_BASE_URL.toString() to baseUrl.toString(),
                CachedOutput.PLACEHOLDER_USERNAME to baseOutput.user.username,
                CachedOutput.PLACEHOLDER_PASSWORD to baseOutput.user.password,
                CachedOutput.PLACEHOLDER_ENCRYPTED_ACCOUNT to "0123abcd",
            ),
            replacements,
        )
        assertTrue(
            baseOutput.copy(type = CachedOutputType.LIVE_CATEGORIES)
                .placeholderReplacements(baseUrl, encryptedAccount = "0123abcd")
                .isEmpty()
        )
    }

    @Test
    fun `output is stale only when cached file predates data update`() {
        val updatedAt = Instant.fromEpochMilliseconds(2_000)

        assertTrue(baseOutput.isStale(1_999, updatedAt))
        assertFalse(baseOutput.isStale(2_000, updatedAt))
        assertFalse(baseOutput.isStale(3_000, updatedAt))
        assertFalse(baseOutput.isStale(0, null))
    }

    private fun user(
        username: String = "viewer",
        password: String = "password-secret",
        maxConnections: UInt = 2u,
        channelBlacklist: List<String> = emptyList(),
        channelWhitelist: List<String> = emptyList(),
        categoryBlacklist: List<String> = emptyList(),
        categoryWhitelist: List<String> = emptyList(),
        moviesEnabled: Boolean = true,
        seriesEnabled: Boolean = true,
    ) = IptvUser(
        username = username,
        password = password,
        maxConnections = maxConnections,
        channelBlacklist = channelBlacklist,
        channelWhitelist = channelWhitelist,
        categoryBlacklist = categoryBlacklist,
        categoryWhitelist = categoryWhitelist,
        moviesEnabled = moviesEnabled,
        seriesEnabled = seriesEnabled,
    )
}
