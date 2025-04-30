package io.github.firstred.iptvproxy.http.headers

import io.github.cdimascio.dotenv.dotenv
import io.github.firstred.iptvproxy.config
import io.github.firstred.iptvproxy.dotenv
import io.github.firstred.iptvproxy.loadConfig
import io.github.firstred.iptvproxy.testConfig
import io.github.firstred.iptvproxy.utils.filterHttpRequestHeaders
import io.ktor.http.*
import org.junit.jupiter.api.BeforeAll
import kotlin.test.Test
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
    fun restRequestHeaderFilter() {
        val headers = headersOf(
            "Accept" to listOf("application/json"),
            "Accept-Charset" to listOf("UTF-8"),
            "Accept-Encoding" to listOf("deflate;q=0.9,gzip;q=0.8"),
            "Proxy-Authorization" to listOf("Basic a2V1cmlnOlJOTDdDdEtzZnhUb3RzQlFmWkRX"),
            "user-agent" to listOf("Dart/3.7 (dart:io)"),
        ).filterHttpRequestHeaders()

        assertNull(headers["accept"])
        assertNull(headers["accept-charset"])
        assertNull(headers["accept-encoding"])
        assert(headers["proxy-authorization"] == "Basic a2V1cmlnOlJOTDdDdEtzZnhUb3RzQlFmWkRX")
        assert(headers["user-agent"] == "Dart/3.7 (dart:io)")
    }
}
