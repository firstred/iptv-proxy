package io.github.firstred.iptvproxy.utils.ktor

import com.ucasoft.ktor.simpleCache.SimpleCacheConfig
import com.ucasoft.ktor.simpleCache.SimpleCacheProvider
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.timer
import kotlin.time.Duration

class SimpleExpiringMemoryCacheProvider(config: Config) : SimpleCacheProvider(config) {
    private val timers: ConcurrentHashMap<String, Timer> = ConcurrentHashMap()
    private val cache: ConcurrentHashMap<String, SimpleExpiringMemoryCacheObject> = ConcurrentHashMap()

    override suspend fun getCache(key: String): Any? {
        val `object` = cache[key]
        return if (`object`?.isExpired != false) {
            null
        } else {
            `object`.content
        }
    }


    override suspend fun setCache(key: String, content: Any, invalidateAt: Duration?) {
        cache[key] = SimpleExpiringMemoryCacheObject(content, invalidateAt ?: this.invalidateAt)

        invalidateAt?.let {
            timers[key] = timer(initialDelay = it.inWholeMilliseconds, period = Long.MAX_VALUE, daemon = true) {
                cache.remove(key)
                cancel()
                timers.remove(key)
            }
        }
    }

    class Config internal constructor() : SimpleCacheProvider.Config()
}

private data class SimpleExpiringMemoryCacheObject(val content: Any, val duration: Duration, val start: Instant = Clock.System.now()) {

    val isExpired: Boolean
        get() = Clock.System.now() - start > duration
}

fun SimpleCacheConfig.expiringMemoryCache(
    configure : SimpleExpiringMemoryCacheProvider.Config.() -> Unit
){
    provider = SimpleExpiringMemoryCacheProvider(SimpleExpiringMemoryCacheProvider.Config().apply(configure))
}
