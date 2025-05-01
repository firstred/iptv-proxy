package io.github.firstred.iptvproxy.proxy

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

class ProxyConfigTest {
    companion object {
        @BeforeAll
        @JvmStatic
        fun setup() {
            dotenv = dotenv { ignoreIfMissing = true }
            config = loadConfig(testConfig)
        }
    }

    @Test
    fun testConfig() {
        assertNull(config.getActualHttpProxyConfiguration()?.username)
    }
}
