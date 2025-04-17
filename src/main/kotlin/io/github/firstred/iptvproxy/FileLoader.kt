package io.github.firstred.iptvproxy

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture

object FileLoader {
    private val LOG: Logger = LoggerFactory.getLogger(FileLoader::class.java)

    private const val FILE_SCHEME = "file://"

    fun tryLoadString(url: String): CompletableFuture<String?>? {
        return try {
            complete(
                loadString(
                    url
                )
            )
        } catch (e: Exception) {
            completeWithError(e)
        }
    }

    fun tryLoadBytes(url: String): CompletableFuture<ByteArray?>? {
        return try {
            complete(
                loadBytes(
                    url
                )
            )
        } catch (e: Exception) {
            completeWithError(e)
        }
    }

    private fun <T> complete(value: T?): CompletableFuture<T>? {
        if (value == null) {
            return null
        }

        val future = CompletableFuture<T>()
        future.complete(value)
        return future
    }

    private fun <T> completeWithError(e: Throwable): CompletableFuture<T> {
        val future = CompletableFuture<T>()
        future.completeExceptionally(e)
        return future
    }

    @Throws(IOException::class)
    private fun loadString(url: String): String? {
        val data = loadBytes(url) ?: return null
        return String(data)
    }

    @Throws(IOException::class)
    private fun loadBytes(url: String): ByteArray? {
        return if (url.startsWith(FILE_SCHEME)) {
            Files.readAllBytes(Path.of(url.substring(FILE_SCHEME.length)))
        } else {
            null
        }
    }
}
