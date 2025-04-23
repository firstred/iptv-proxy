package io.github.firstred.iptvproxy.listeners

import io.github.firstred.iptvproxy.events.ChannelsUpdatedEvent
import io.github.firstred.iptvproxy.listeners.hooks.HasOnApplicationEventHook
import org.koin.core.component.KoinComponent
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class HealthListener : KoinComponent, HasOnApplicationEventHook {
    private var channelsAvailable = false

    fun isReady(): Boolean {
        return channelsAvailable
    }

    fun isLive(): Boolean {
        return channelsAvailable
    }

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(HealthListener::class.java)
    }

    override fun <T : Any> onApplicationEvent(event: T) {
        when (event) {
            is ChannelsUpdatedEvent -> {
                channelsAvailable = true
            }
        }
    }
}
