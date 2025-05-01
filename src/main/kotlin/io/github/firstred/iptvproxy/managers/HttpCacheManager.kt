package io.github.firstred.iptvproxy.managers

import io.github.firstred.iptvproxy.config
import io.github.firstred.iptvproxy.listeners.hooks.lifecycle.HasApplicationOnStartHook
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class HttpCacheManager : KoinComponent, HasApplicationOnStartHook {
    private val scheduledExecutorService: ScheduledExecutorService by inject()

    private fun scheduleCleanups(delay: Long = 0, unit: TimeUnit = TimeUnit.MINUTES) {
        scheduledExecutorService.schedule(
            Thread {
                try {
                    runBlocking {
                        cleanCache()
                        scheduleCleanups(config.clientHttpCache.cleanupInterval.inWholeMinutes)
                    }
                } catch (e: InterruptedException) {
                    LOG.info("Scheduler interrupted while cleaning cache", e)
                } catch (e: Exception) {
                    LOG.error("Error while cleaning cache", e)
                    runBlocking {
                        scheduleCleanups(config.clientHttpCache.cleanupInterval.inWholeMinutes)
                    }
                }
            },
            delay,
            unit,
        )
    }

    fun cleanCache() {
        runBlocking {
            awaitAll(
    async { cleanIcons() },
                async { cleanVideoChunks() },
            )
        }
    }

    fun cleanIcons() {
        val iconDir = File(config.getActualHttpCacheDirectory("images"))
        if (!iconDir.exists() || !iconDir.isDirectory) return

        for (cacheFile in iconDir.listFiles()) {
            if (cacheFile.lastModified() < (System.currentTimeMillis() - config.clientHttpCache.ttl.icons.inWholeMilliseconds)) {
                cacheFile.delete()
            }
        }
    }

    fun cleanVideoChunks() {
        val iconDir = File(config.getActualHttpCacheDirectory("chunks"))
        if (!iconDir.exists() || !iconDir.isDirectory) return

        for (cacheFile in iconDir.listFiles()) {
            if (cacheFile.lastModified() < (System.currentTimeMillis() - config.clientHttpCache.ttl.videoChunks.inWholeMilliseconds)) {
                cacheFile.delete()
            }
        }
    }

    override fun onApplicationStartHook() {
        if (!config.clientHttpCache.enabled) {
            LOG.info("Client HTTP cache is disabled -- skipping cache manager startup")
            return
        }

        LOG.info("Cache manager starting")
        scheduleCleanups()
        LOG.info("Cache manager started")
    }

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(HttpCacheManager::class.java)
    }
}
