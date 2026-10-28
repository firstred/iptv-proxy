package io.github.firstred.iptvproxy.utils

import io.github.firstred.iptvproxy.classes.CachedOutput
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

    @Test
    fun `copies input without matches unchanged`() {
        assertEquals("", "".replacing("{base}", "https://iptv.example/"))
        assertEquals("no placeholder here", "no placeholder here".replacing("{base}", "https://iptv.example/"))
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
    fun `injected base url matches output rendered with the actual base url`() {
        val placeholder = CachedOutput.PLACEHOLDER_BASE_URL
        val actual = URI("https://iptv.example:8443/sub/path/")
        val render = { baseUrl: URI ->
            "#EXTM3U\n" +
                "#EXTINF:-1 tvg-logo=\"${baseUrl}images/account/0123abcd/logo.png\",Channel\n" +
                "${baseUrl.resolve("live/viewer/secret/1.m3u8")}\n"
        }

        assertEquals(render(actual), render(placeholder).replacing(placeholder.toString(), actual.toString(), 7))
    }
}
