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

    /**
     * Schedules periodic cache cleanup tasks with an optional initial delay and time unit for scheduling.
     * Handles exceptions during execution and reschedules on failure.
     *
     * @param delay The initial delay before the cleanup task is executed. Defaults to 0.
     * @param unit The time unit for the delay parameter. Defaults to TimeUnit.MINUTES.
     */
    private fun scheduleCleanups(delay: Long = 0, unit: TimeUnit = TimeUnit.MINUTES) {
        scheduledExecutorService.schedule(
            Thread {
                // Schedules periodic cache cleanup with error handling
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

    /**
     * Cleans the cache directory for images by removing files that have exceeded their time-to-live (TTL).
     *
     * This method checks if the cache is enabled in the configuration. If enabled, it targets the cache directory
     * for HTTP image files and deletes files whose last modified timestamp exceeds the configured TTL for images.
     *
     * The determination of which files to delete is based on the `cleanDirectory` method, which evaluates file last
     * modification times and removes outdated files.
     */
    fun cleanCache() {
        if (!config.cache.enabled) return

        cleanDirectory(
            File(config.getHttpCacheDirectory("images")),
            config.cache.ttl.images.inWholeMilliseconds,
        )
    }

    /**
     * Deletes files in the specified directory that have not been modified within the specified time-to-live (TTL) period.
     *
     * @param directory The directory whose files should be cleaned. Only valid directories are processed.
     * @param ttlInWholeMilliseconds The time-to-live (TTL) in milliseconds. Files last modified before the current
     *                                time minus this TTL are deleted.
     */
    fun cleanDirectory(directory: File, ttlInWholeMilliseconds: Long) {
        if (!directory.exists() || !directory.isDirectory) return

        // Deletes files older than configured TTL
        for (cacheFile in directory.listFiles()) {
            if (cacheFile.lastModified() < (System.currentTimeMillis() - ttlInWholeMilliseconds)) {
                cacheFile.delete()
            }
        }
    }

    /**
     * Hook that is executed when the application starts.
     *
     * This method is responsible for initializing the cache management
     * process. It logs the start of the HTTP cache manager, schedules
     * periodic cache cleanup tasks, and confirms the successful startup
     * of these operations in the logs.
     *
     * Delegates the actual scheduling of cleanup tasks to the `scheduleCleanups`
     * method, ensuring the cleanup process runs periodically as configured.
     */
    override fun onApplicationStartHook() {
        LOG.trace("Http cache manager starting")
        scheduleCleanups()
        LOG.trace("Http cache manager started")
    }

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(CacheManager::class.java)
    }
}
