package io.github.firstred.iptvproxy.classes

import io.ktor.client.plugins.cache.storage.CacheStorage
import io.ktor.client.plugins.cache.storage.CachedResponseData
import io.ktor.http.HeadersBuilder
import io.ktor.http.HttpProtocolVersion
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.util.flattenEntries
import io.ktor.util.date.GMTDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.io.Sink
import kotlinx.io.Source
import kotlinx.io.buffered
import kotlinx.io.readByteArray
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readLineStrict
import kotlinx.io.writeString
import org.slf4j.LoggerFactory
import java.security.MessageDigest

/**
 * HTTP client cache storage that keeps cached responses exclusively on disk.
 *
 * Ktor's `FileStorage` wraps its file storage in an in-memory cache that keeps every response body ever read or
 * written, which is never evicted. This storage reads entries from disk on every lookup instead, so the memory usage
 * only depends on the responses that are currently in use.
 *
 * The file format is identical to the one used by Ktor's `FileStorage`, existing cache files therefore remain valid.
 */
class DiskCacheStorage(
    private val directory: Path,
    private val fileSystem: FileSystem = SystemFileSystem,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : CacheStorage {
    // A fixed number of locks, so the lock table does not grow with the number of cached urls
    private val locks = Array(LOCK_STRIPES) { Mutex() }

    init {
        fileSystem.createDirectories(directory)
    }

    override suspend fun store(url: Url, data: CachedResponseData) = update(url) { entries ->
        entries.filterNot { it.varyKeys == data.varyKeys } + data
    }

    override suspend fun find(url: Url, varyKeys: Map<String, String>): CachedResponseData? =
        findAll(url).find { entry -> varyKeys.all { (key, value) -> entry.varyKeys[key] == value } }

    override suspend fun findAll(url: Url): Set<CachedResponseData> {
        val key = key(url)
        return withContext(dispatcher) {
            lockFor(key).withLock { read(key) }
        }
    }

    override suspend fun remove(url: Url, varyKeys: Map<String, String>) = update(url) { entries ->
        entries.filterNot { it.varyKeys == varyKeys }
    }

    override suspend fun removeAll(url: Url) {
        val key = key(url)
        withContext(dispatcher) {
            lockFor(key).withLock { delete(path(key)) }
        }
    }

    override suspend fun clear() {
        withContext(dispatcher) {
            fileSystem.list(directory).forEach { delete(it) }
        }
    }

    private suspend fun update(url: Url, transform: (Set<CachedResponseData>) -> List<CachedResponseData>) {
        val key = key(url)
        withContext(dispatcher) {
            lockFor(key).withLock {
                val entries = transform(read(key))
                if (entries.isEmpty()) delete(path(key)) else write(key, entries)
            }
        }
    }

    private fun read(key: String): Set<CachedResponseData> {
        val path = path(key)
        if (!fileSystem.exists(path)) return emptySet()

        return try {
            fileSystem.source(path).buffered().use { source ->
                val count = source.readInt()
                buildSet { repeat(count) { add(source.readEntry()) } }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LOG.trace("Unable to read http cache file {}, ignoring it", path, e)
            emptySet()
        }
    }

    private fun write(key: String, entries: List<CachedResponseData>) {
        val path = path(key)
        // Write to a temporary file first, so a concurrent cleanup or crash never leaves a truncated entry behind
        val temporaryPath = Path(directory, "$key.tmp")
        try {
            fileSystem.sink(temporaryPath).buffered().use { sink ->
                sink.writeInt(entries.size)
                entries.forEach { sink.writeEntry(it) }
            }
            fileSystem.atomicMove(temporaryPath, path)
        } catch (e: CancellationException) {
            delete(temporaryPath)
            throw e
        } catch (e: Exception) {
            delete(temporaryPath)
            LOG.trace("Unable to write http cache file {}", path, e)
        }
    }

    private fun delete(path: Path) {
        try {
            fileSystem.delete(path, mustExist = false)
        } catch (e: Exception) {
            LOG.trace("Unable to delete http cache file {}", path, e)
        }
    }

    private fun Sink.writeEntry(entry: CachedResponseData) {
        writeString(entry.url.toString() + "\n")
        writeInt(entry.statusCode.value)
        writeString(entry.statusCode.description + "\n")
        writeString(entry.version.toString() + "\n")
        val headers = entry.headers.flattenEntries()
        writeInt(headers.size)
        headers.forEach { (name, value) ->
            writeString(name + "\n")
            writeString(value + "\n")
        }
        writeLong(entry.requestTime.timestamp)
        writeLong(entry.responseTime.timestamp)
        writeLong(entry.expires.timestamp)
        writeInt(entry.varyKeys.size)
        entry.varyKeys.forEach { (name, value) ->
            writeString(name + "\n")
            writeString(value + "\n")
        }
        writeInt(entry.body.size)
        write(entry.body)
    }

    private fun Source.readEntry(): CachedResponseData {
        val url = readLineStrict()
        val status = HttpStatusCode(readInt(), readLineStrict())
        val version = HttpProtocolVersion.parse(readLineStrict())
        val headers = HeadersBuilder().apply {
            repeat(readInt()) { append(readLineStrict(), readLineStrict()) }
        }
        val requestTime = GMTDate(readLong())
        val responseTime = GMTDate(readLong())
        val expires = GMTDate(readLong())
        val varyKeys = buildMap {
            repeat(readInt()) { put(readLineStrict().lowercase(), readLineStrict()) }
        }
        val body = readByteArray(readInt())

        return CachedResponseData(
            url = Url(url),
            statusCode = status,
            requestTime = requestTime,
            responseTime = responseTime,
            version = version,
            expires = expires,
            headers = headers.build(),
            varyKeys = varyKeys,
            body = body,
        )
    }

    private fun path(key: String) = Path(directory, key)

    private fun lockFor(key: String) = locks[Math.floorMod(key.hashCode(), LOCK_STRIPES)]

    private fun key(url: Url): String = MessageDigest.getInstance("SHA-256")
        .digest(url.toString().encodeToByteArray())
        .joinToString("") { "%02x".format(it) }

    companion object {
        private val LOG = LoggerFactory.getLogger(DiskCacheStorage::class.java)
        private const val LOCK_STRIPES = 64
    }
}
