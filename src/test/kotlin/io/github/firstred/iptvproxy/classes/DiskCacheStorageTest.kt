package io.github.firstred.iptvproxy.classes

import io.ktor.client.plugins.cache.storage.CachedResponseData
import io.ktor.client.plugins.cache.storage.FileStorage
import io.ktor.http.HttpProtocolVersion
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.headersOf
import io.ktor.util.date.GMTDate
import kotlinx.coroutines.runBlocking
import kotlinx.io.files.Path
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DiskCacheStorageTest {
    private lateinit var directory: File

    @BeforeTest
    fun setup() {
        directory = Files.createTempDirectory("iptv-proxy-disk-cache-storage").toFile()
    }

    @AfterTest
    fun tearDown() {
        directory.deleteRecursively()
    }

    @Test
    fun `stored entries are found per vary keys and removed again`() = runBlocking {
        val storage = DiskCacheStorage(Path(directory.path))
        val url = Url("https://example.com/icon.png")

        storage.store(url, entry(url, mapOf("accept" to "image/png"), byteArrayOf(1, 2, 3)))
        storage.store(url, entry(url, mapOf("accept" to "image/webp"), byteArrayOf(4, 5)))

        assertEquals(2, storage.findAll(url).size)
        val png = assertNotNull(storage.find(url, mapOf("accept" to "image/png")))
        assertContentEquals(byteArrayOf(1, 2, 3), png.body)
        assertEquals("image/png", png.headers["Content-Type"])
        assertEquals(HttpStatusCode.OK, png.statusCode)

        storage.remove(url, mapOf("accept" to "image/png"))
        assertNull(storage.find(url, mapOf("accept" to "image/png")))
        assertNotNull(storage.find(url, mapOf("accept" to "image/webp")))

        storage.removeAll(url)
        assertTrue(storage.findAll(url).isEmpty())
        assertTrue(directory.listFiles().isNullOrEmpty())
    }

    @Test
    fun `entries are not kept in memory once their file is gone`() = runBlocking {
        val storage = DiskCacheStorage(Path(directory.path))
        val url = Url("https://example.com/icon.png")

        storage.store(url, entry(url, emptyMap(), ByteArray(1024) { it.toByte() }))
        assertNotNull(storage.find(url, emptyMap()))

        // E.g. removed by the periodic image cache cleanup
        directory.listFiles()!!.forEach { it.delete() }

        assertNull(storage.find(url, emptyMap()))
    }

    @Test
    fun `files written by ktor's file storage remain readable and vice versa`() = runBlocking {
        val url = Url("https://example.com/icon.png?size=large")
        val body = ByteArray(4096) { (it % 251).toByte() }

        FileStorage(Path(directory.path)).store(url, entry(url, mapOf("accept" to "image/png"), body))
        val fromKtor = assertNotNull(DiskCacheStorage(Path(directory.path)).find(url, mapOf("accept" to "image/png")))
        assertContentEquals(body, fromKtor.body)
        assertEquals(1, directory.listFiles()!!.size)

        val otherUrl = Url("https://example.com/other.png")
        DiskCacheStorage(Path(directory.path)).store(otherUrl, entry(otherUrl, emptyMap(), body))
        val fromDisk = assertNotNull(FileStorage(Path(directory.path)).find(otherUrl, emptyMap()))
        assertContentEquals(body, fromDisk.body)
        assertEquals(2, directory.listFiles()!!.size)
    }

    private fun entry(url: Url, varyKeys: Map<String, String>, body: ByteArray) = CachedResponseData(
        url = url,
        statusCode = HttpStatusCode.OK,
        requestTime = GMTDate(1_000L),
        responseTime = GMTDate(2_000L),
        version = HttpProtocolVersion.HTTP_1_1,
        expires = GMTDate(3_000L),
        headers = headersOf("Content-Type", varyKeys["accept"] ?: "image/png"),
        varyKeys = varyKeys,
        body = body,
    )
}
