package io.github.firstred.iptvproxy.http.headers

import io.github.cdimascio.dotenv.dotenv
import io.github.firstred.iptvproxy.config
import io.github.firstred.iptvproxy.dotenv
import io.github.firstred.iptvproxy.loadConfig
import io.github.firstred.iptvproxy.testConfig
import io.github.firstred.iptvproxy.utils.filterAndAppendHttpRequestHeaders
import io.github.firstred.iptvproxy.utils.filterHttpResponseHeaders
import io.ktor.http.*
import org.junit.jupiter.api.BeforeAll
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HeadersTest {
    companion object {
        @BeforeAll
        @JvmStatic
        fun setup() {
            dotenv = dotenv { ignoreIfMissing = true }
            config = loadConfig(testConfig)
        }
    }

    @Test
    fun restRequestHeadersFilter() {
        val headers = headersOf(
            "Accept" to listOf("application/json"),
            "Accept-Charset" to listOf("UTF-8"),
            "Accept-Encoding" to listOf("deflate;q=0.9,gzip;q=0.8"),
            "Proxy-Authorization" to listOf("Basic a2V1cmlnOlJOTDdDdEtzZnhUb3RzQlFmWkRX"),
            "user-agent" to listOf("Dart/3.7 (dart:io)"),
        ).filterAndAppendHttpRequestHeaders()

        assertNull(headers["accept"])
        assertNull(headers["accept-charset"])
        assertNull(headers["accept-encoding"])
        assertEquals("Basic a2V1cmlnOlJOTDdDdEtzZnhUb3RzQlFmWkRXh", headers["proxy-authorization"])
        assertEquals("Dart/3.7 (dart:io)", headers["user-agent"])
    }

    @Test
    fun restRangeResponseHeadersFilter() {
        /**
         * -> accept-ranges: 0-1723159133
         * -> access-control-allow-origin: *
         * -> content-length: 1723159133
         * -> content-range: bytes 0-1723159132/1723159133
         * -> content-type: video/mp4
         * -> date: Thu, 01 May 2025 12:29:40 GMT
         */
        val headers = headersOf(
            "Accept-Ranges" to listOf("0-1723159133"),
            "Access-Control-Allow-Origin" to listOf("*"),
            "Content-Length" to listOf("1723159133"),
            "Content-Range" to listOf("bytes 0-1723159132/1723159133"),
            "Content-Type" to listOf("video/mp4"),
            "Date" to listOf("Thu, 01 May 2025 12:29:40 GMT"),
        ).filterHttpResponseHeaders()

        assertEquals("0-1723159133", headers["accept-ranges"])
        assertNull(headers["access-control-allow-origin"])
        assertNull(headers["content-length"])
        assertEquals("bytes 0-1723159132/1723159133", headers["content-range"])
        assertEquals("video/mp4", headers["content-type"])
        assertEquals("Thu, 01 May 2025 12:29:40 GMT", headers["date"])
    }
}
