package io.github.firstred.iptvproxy.managers

import io.github.firstred.iptvproxy.config
import io.github.firstred.iptvproxy.listeners.hooks.lifecycle.HasApplicationOnStartHook
import io.sentry.MonitorConfig
import io.sentry.util.CheckInUtils
import kotlinx.coroutines.runBlocking
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.qualifier.named
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

@Suppress("UnstableApiUsage")
class CacheManager : KoinComponent, HasApplicationOnStartHook {
    private val scheduledExecutorService: ScheduledExecutorService by inject()
    private val cleanupMonitorConfig: MonitorConfig by inject(named("cleanup-cache"))

    private fun scheduleCleanups(delay: Long = 0, unit: TimeUnit = TimeUnit.MINUTES) {
        scheduledExecutorService.schedule(
            Thread {
                try {
                    CheckInUtils.withCheckIn("cleanup-cache", cleanupMonitorConfig) {
                        runBlocking {
                            cleanCache()
                            scheduleCleanups(config.cleanupInterval.inWholeMinutes)
                        }
                    }
                } catch (e: InterruptedException) {
                    LOG.warn("Scheduler interrupted while cleaning cache", e)
                } catch (e: Exception) {
                    LOG.error("Error while cleaning cache", e)
                    runBlocking {
                        scheduleCleanups(config.cleanupInterval.inWholeMinutes)
                    }
                }
            },
            delay,
            unit,
        )
    }

    fun cleanCache() {
        if (!config.cache.enabled) return

        cleanDirectory(
            File(config.getHttpCacheDirectory("images")),
            config.cache.ttl.images.inWholeMilliseconds,
        )
    }

    fun cleanDirectory(directory: File, ttlInWholeMilliseconds: Long) {
        if (!directory.exists() || !directory.isDirectory) return

        for (cacheFile in directory.listFiles()) {
            if (cacheFile.lastModified() < (System.currentTimeMillis() - ttlInWholeMilliseconds)) {
                cacheFile.delete()
            }
        }
    }

    override fun onApplicationStartHook() {
        LOG.trace("Http cache manager starting")
        scheduleCleanups()
        LOG.trace("Http cache manager started")
    }

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(CacheManager::class.java)
    }
}
