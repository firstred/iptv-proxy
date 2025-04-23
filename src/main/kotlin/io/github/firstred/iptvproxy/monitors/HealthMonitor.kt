package io.github.firstred.iptvproxy.monitors

import io.github.firstred.iptvproxy.events.ChannelsUpdatedEvent
import io.github.firstred.iptvproxy.listeners.HasOnApplicationEventHook
import org.koin.core.component.KoinComponent
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class HealthMonitor : KoinComponent, HasOnApplicationEventHook {
    private var channelsAvailable = false

    fun isReady(): Boolean {
        return channelsAvailable
    }

    fun isLive(): Boolean {
        return channelsAvailable
    }

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(HealthMonitor::class.java)
    }

    override fun <T : Any> onApplicationEvent(event: T) {
        if (event is ChannelsUpdatedEvent) {
            channelsAvailable = true
        }
    }
}
