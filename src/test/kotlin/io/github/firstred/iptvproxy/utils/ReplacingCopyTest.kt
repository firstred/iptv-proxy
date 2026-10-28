package io.github.firstred.iptvproxy.utils

import io.github.firstred.iptvproxy.classes.CachedOutput
import io.github.firstred.iptvproxy.classes.CachedOutputType
import io.github.firstred.iptvproxy.classes.IptvUser
import io.github.firstred.iptvproxy.classes.toCachedOutputSettings
import io.github.firstred.iptvproxy.serialization.json
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import java.io.ByteArrayOutputStream
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals

class ReplacingCopyTest {
    private fun String.replacing(search: String, replacement: String, bufferSize: Int = DEFAULT_BUFFER_SIZE): String {
        val output = ByteArrayOutputStream()
        byteInputStream().copyReplacingTo(output, search.toByteArray(), replacement.toByteArray(), bufferSize)
        return output.toString(Charsets.UTF_8)
    }

    private fun String.replacing(replacements: List<Pair<String, String>>, bufferSize: Int = DEFAULT_BUFFER_SIZE): String =
        replacingBytes(replacements.map { (search, replacement) -> search.toByteArray() to replacement.toByteArray() }, bufferSize)

    private fun String.replacingBytes(replacements: List<Pair<ByteArray, ByteArray>>, bufferSize: Int = DEFAULT_BUFFER_SIZE): String {
        val output = ByteArrayOutputStream()
        byteInputStream().copyReplacingTo(output, replacements, bufferSize)
        return output.toString(Charsets.UTF_8)
    }

    @Test
    fun `copies input without matches unchanged`() {
        assertEquals("", "".replacing("{base}", "https://iptv.example/"))
        assertEquals("no placeholder here", "no placeholder here".replacing("{base}", "https://iptv.example/"))
        assertEquals("no placeholder here", "no placeholder here".replacing(listOf()))
    }

    @Test
    fun `replaces every occurrence regardless of buffer size`() {
        val input = "{base}a{base}{base}bc{base}"
        val expected = "XaXXbcX"

        (1..input.length + 1).forEach { bufferSize ->
            assertEquals(expected, input.replacing("{base}", "X", bufferSize), "buffer size $bufferSize")
        }
    }

    @Test
    fun `keeps partial matches at the end of the stream`() {
        (1..8).forEach { bufferSize ->
            assertEquals("ab{bas", "ab{bas".replacing("{base}", "X", bufferSize), "buffer size $bufferSize")
            assertEquals("{{X", "{{{base}".replacing("{base}", "X", bufferSize), "buffer size $bufferSize")
        }
    }

    @Test
    fun `replaces multiple sequences of different lengths regardless of buffer size`() {
        val replacements = listOf("{a}" to "1", "{bb}" to "22", "{ccc}" to "333", "{d}" to "")
        val input = "{a}x{bb}{a}{ccc}{d}y{cc{a}"
        val expected = "1x221333y{cc1"

        (1..input.length + 1).forEach { bufferSize ->
            assertEquals(expected, input.replacing(replacements, bufferSize), "buffer size $bufferSize")
        }
    }

    @Test
    fun `replaces shorter sequences at the end of the stream`() {
        val replacements = listOf("{a}" to "1", "{longer}" to "2")

        (1..10).forEach { bufferSize ->
            assertEquals("x1", "x{a}".replacing(replacements, bufferSize), "buffer size $bufferSize")
            assertEquals("{a", "{a".replacing(replacements, bufferSize), "buffer size $bufferSize")
        }
    }

    @Test
    fun `first matching sequence wins`() {
        assertEquals("1c1", "abcab".replacing(listOf("ab" to "1", "abc" to "2")))
        assertEquals("21", "abcab".replacing(listOf("abc" to "2", "ab" to "1")))
    }

    @Test
    fun `shared playlist matches the playlist rendered for the requesting user`() {
        val actualBaseUrl = URI("https://iptv.example:8443/sub/path/")
        val actualUser = IptvUser(username = "viewer", password = "s3cret.pass-word*", maxConnections = 2u)
        val placeholderUser = actualUser.toCachedOutputSettings().toPlaceholderUser()
        val render = { user: IptvUser, baseUrl: URI, encryptedAccount: String ->
            "#EXTM3U\n" +
                "#EXTINF:-1 tvg-logo=\"${baseUrl}images/$encryptedAccount/0123abcd/logo.png\",Channel\n" +
                "${baseUrl.resolve("live/${user.username}/${user.password}/1.m3u8")}\n"
        }

        val shared = render(placeholderUser, CachedOutput.PLACEHOLDER_BASE_URL, CachedOutput.PLACEHOLDER_ENCRYPTED_ACCOUNT)
        val replacements = CachedOutput(CachedOutputType.PLAYLIST, actualUser)
            .placeholderReplacements(actualBaseUrl, encryptedAccount = "c0ffee")

        (1..16).forEach { bufferSize ->
            assertEquals(render(actualUser, actualBaseUrl, "c0ffee"), shared.replacingBytes(replacements, bufferSize))
        }
    }

    @Test
    fun `shared xtream list matches the list rendered for the requesting user`() {
        val actualBaseUrl = URI("https://iptv.example/")
        val actualUser = IptvUser(username = "viewer", password = "s3cret", maxConnections = 2u)
        val placeholderUser = actualUser.toCachedOutputSettings().toPlaceholderUser()
        val render = { user: IptvUser, baseUrl: URI, encryptedAccount: String ->
            json.encodeToString(
                MapSerializer(String.serializer(), String.serializer()),
                mapOf(
                    "stream_icon" to "${baseUrl}images/$encryptedAccount/0123abcd/logo.png",
                    "url" to "${baseUrl}live/${user.username}/${user.password}/1.ts",
                ),
            )
        }

        val shared = render(placeholderUser, CachedOutput.PLACEHOLDER_BASE_URL, CachedOutput.PLACEHOLDER_ENCRYPTED_ACCOUNT)
        val replacements = CachedOutput(CachedOutputType.LIVE_STREAMS, actualUser)
            .placeholderReplacements(actualBaseUrl, encryptedAccount = "c0ffee")

        assertEquals(render(actualUser, actualBaseUrl, "c0ffee"), shared.replacingBytes(replacements, 7))
    }
}
