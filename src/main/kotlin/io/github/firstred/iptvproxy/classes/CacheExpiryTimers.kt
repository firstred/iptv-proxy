package io.github.firstred.iptvproxy.classes

import com.mayakapps.kache.FileKache
import io.github.firstred.iptvproxy.listeners.hooks.lifecycle.HasApplicationOnTerminateHook
import kotlinx.coroutines.runBlocking
import org.koin.core.qualifier.named
import org.koin.mp.KoinPlatform.getKoin
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.timer

class CacheExpiryTimers : ConcurrentHashMap<String, Timer>(), HasApplicationOnTerminateHook {
    fun add(key: String, cacheName: String, delayInMilliseconds: Long) {
        this["$cacheName|$key"]?.cancel()
        this["$cacheName|$key"] = timer(initialDelay = delayInMilliseconds, period = Long.MAX_VALUE, daemon = true) {
            val cache: FileKache = getKoin().get(named(cacheName))
            runBlocking {
                cache.remove("$cacheName|$key")
            }
            this@CacheExpiryTimers["$cacheName|$key"]?.cancel()
            this@CacheExpiryTimers.remove("$cacheName|$key")
        }
    }

    fun cancel(key: String, cacheName: String) {
        this["$cacheName|$key"]?.cancel()
        this.remove("$cacheName|$key")
    }

    fun cancelAll() {
        forEach { (_, timer) ->
            timer.cancel()
        }
        this.clear()
    }

    fun cancelAndRemove(key: String, cacheName: String) {
        this["$cacheName|$key"]?.cancel()
        this.remove("$cacheName|$key")
    }

    override fun onApplicationTerminateHook() {
        this.cancelAll()
    }
}
