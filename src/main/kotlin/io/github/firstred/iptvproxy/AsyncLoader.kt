package io.github.firstred.iptvproxy

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.HttpResponse.BodyHandler
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.function.Supplier

class AsyncLoader<T>(
    private val timeoutSec: Long,
    private val totalTimeoutSec: Long,
    private val retryDelayMs: Long,
    private val scheduler: ScheduledExecutorService,
    private val handlerSupplier: Supplier<BodyHandler<T>>
) {
    fun loadAsync(msg: String?, url: String, httpClient: HttpClient): CompletableFuture<T?> {
        return loadAsync(msg, HttpRequest.newBuilder().uri(URI.create(url)).build(), httpClient)
    }

    fun loadAsync(msg: String?, req: HttpRequest, httpClient: HttpClient): CompletableFuture<T?> {
        val rid = RequestCounter.next()

        val future = CompletableFuture<T?>()
        loadAsync(
            msg,
            req,
            0,
            System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(totalTimeoutSec),
            rid,
            future,
            httpClient
        )
        return future
    }

    private fun loadAsync(
        msg: String?,
        req: HttpRequest,
        retryNo: Int,
        expireTime: Long,
        rid: String,
        future: CompletableFuture<T?>,
        httpClient: HttpClient
    ) {
        LOG.info("{}loading {}, retry: {}, url: {}", rid, msg, retryNo, req.uri())

        val startNanos = System.nanoTime()
        httpClient.sendAsync(req, handlerSupplier.get())
            .orTimeout(timeoutSec, TimeUnit.SECONDS)
            .whenComplete { resp: HttpResponse<T>, err: Throwable? ->
                if (HttpUtils.isOk(resp, err, rid, startNanos)) {
                    future.complete(resp.body())
                } else {
                    if (System.currentTimeMillis() < expireTime) {
                        LOG.warn("{}will retry", rid)

                        scheduler.schedule(
                            { loadAsync(msg, req, retryNo + 1, expireTime, rid, future, httpClient) },
                            retryDelayMs,
                            TimeUnit.MILLISECONDS
                        )
                    } else {
                        LOG.error("{}failed", rid)
                        future.complete(null)
                    }
                }
            }
    }

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(AsyncLoader::class.java)

        fun stringLoader(
            timeoutSec: Long,
            totalTimeoutSec: Long,
            retryDelayMs: Long,
            scheduler: ScheduledExecutorService
        ): AsyncLoader<String> {
            return AsyncLoader(
                timeoutSec, totalTimeoutSec, retryDelayMs, scheduler
            ) { HttpResponse.BodyHandlers.ofString() }
        }

        fun bytesLoader(
            timeoutSec: Long,
            totalTimeoutSec: Long,
            retryDelayMs: Long,
            scheduler: ScheduledExecutorService
        ): AsyncLoader<ByteArray> {
            return AsyncLoader(
                timeoutSec, totalTimeoutSec, retryDelayMs, scheduler
            ) { HttpResponse.BodyHandlers.ofByteArray() }
        }
    }
}
