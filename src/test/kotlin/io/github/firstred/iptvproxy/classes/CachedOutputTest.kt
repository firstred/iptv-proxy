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
        user = IptvUser(
            username = "viewer",
            password = "password-secret",
            maxConnections = 2u,
        ),
    )

    @Test
    fun `cache key is stable for the same input`() {
        assertEquals(baseOutput.cacheKey("config"), baseOutput.cacheKey("config"))
        assertNotEquals(baseOutput.cacheKey("config"), baseOutput.cacheKey("different-config"))
    }

    @Test
    fun `cache key differs for user settings`() {
        assertNotEquals(baseOutput.cacheKey("config"), baseOutput.copy(user = baseOutput.user.copyForTest(password = "different")).cacheKey("config"))
        assertNotEquals(
            baseOutput.cacheKey("config"),
            baseOutput.copy(user = baseOutput.user.copyForTest(channelBlacklist = listOf("Blocked"))).cacheKey("config"),
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
    fun `cache key does not contain user password`() {
        assertFalse(baseOutput.cacheKey("config").contains(baseOutput.user.password))
    }

    @Test
    fun `base url is encoded for the output format`() {
        val baseUrl = URI("https://iptv.example/path/?a=1&b='2'")

        assertEquals("https://iptv.example/path/?a=1&b='2'", baseOutput.encodeBaseUrl(baseUrl).decodeToString())
        assertEquals(
            "https://iptv.example/path/?a=1&amp;b=&apos;2&apos;",
            baseOutput.copy(type = CachedOutputType.XMLTV).encodeBaseUrl(baseUrl).decodeToString(),
        )
        assertEquals(
            "https://iptv.example/path/?a=1&b='2'",
            baseOutput.copy(type = CachedOutputType.LIVE_STREAMS).encodeBaseUrl(baseUrl).decodeToString(),
        )
    }

    @Test
    fun `placeholder base url is identical in every output format`() {
        val placeholder = CachedOutput.PLACEHOLDER_BASE_URL.toString()

        CachedOutputType.entries.forEach { type ->
            assertEquals(placeholder, baseOutput.copy(type = type).encodeBaseUrl(CachedOutput.PLACEHOLDER_BASE_URL).decodeToString())
        }
    }

    @Test
    fun `output is stale only when cached file predates data update`() {
        val updatedAt = Instant.fromEpochMilliseconds(2_000)

        assertTrue(baseOutput.isStale(1_999, updatedAt))
        assertFalse(baseOutput.isStale(2_000, updatedAt))
        assertFalse(baseOutput.isStale(3_000, updatedAt))
        assertFalse(baseOutput.isStale(0, null))
    }

    private fun IptvUser.copyForTest(
        password: String = this.password,
        channelBlacklist: List<String> = this.channelBlacklist,
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