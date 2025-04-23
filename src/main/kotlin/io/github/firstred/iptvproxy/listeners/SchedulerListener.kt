package io.github.firstred.iptvproxy.listeners

import io.github.firstred.iptvproxy.config
import io.github.firstred.iptvproxy.listeners.lifecycle.HasApplicationOnStartHook
import io.github.firstred.iptvproxy.listeners.lifecycle.HasApplicationOnTerminateHook
import io.github.firstred.iptvproxy.managers.ChannelManager
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.slf4j.LoggerFactory
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class SchedulerListener : KoinComponent, HasApplicationOnStartHook, HasApplicationOnTerminateHook {
    private val scheduledExecutorService: ScheduledExecutorService  by inject()
    private val channelManager: ChannelManager by inject()

    override fun onApplicationStartHook() {
        LOG.info("Scheduler starting")

        scheduleUpdateChannels()

        LOG.info("Scheduler started")
    }

    override fun onApplicationTerminateHook() {
        LOG.info("Scheduler stopping")

        try {
            scheduledExecutorService.shutdownNow()
            if (!scheduledExecutorService.awaitTermination(1, TimeUnit.MINUTES)) {
                LOG.warn("Scheduler is still running...")
                scheduledExecutorService.shutdownNow()
            }
        } catch (e: InterruptedException) {
            LOG.error("Interrupted while stopping scheduler")
        }

        LOG.info("Scheduler stopped")
    }

    private fun scheduleUpdateChannels(delay: Long = 0, unit: TimeUnit = TimeUnit.MINUTES) {
        scheduledExecutorService.schedule(
            Thread {
                try {
                    channelManager.updateChannels()
                    scheduleUpdateChannels(config.updateInterval.inWholeMinutes)
                } catch (e: InterruptedException) {
                    LOG.info("Scheduler interrupted while updating channels", e)
                } catch (e: Exception) {
                    LOG.error("Error while updating channels", e)
                    scheduleUpdateChannels(1)
                }
            },
            delay,
            unit,
        )
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(SchedulerListener::class.java)
    }
}
