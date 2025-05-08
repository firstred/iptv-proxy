package io.github.firstred.iptvproxy.url

import io.ktor.http.Url
import kotlin.test.Test
import kotlin.test.assertEquals

class UrlTest {
    @Test
    fun dummyTest() {
        val url = Url("index.m3u8")
        assertEquals("localhost", url.host)
    }
}
