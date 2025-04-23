package io.github.firstred.iptvproxy.monitors

import io.github.firstred.iptvproxy.events.ChannelsUpdatedEvent
import io.github.firstred.iptvproxy.events.EventBus
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class HealthMonitor : KoinComponent {
    private val eventBus: EventBus by inject()
    private var channelsAvailable = false

    init {
        EventBus.coroutineScope.launch {
            eventBus.flow.collect {
                if (it is ChannelsUpdatedEvent) channelsAvailable = true
            }
        }
    }

    fun isReady(): Boolean {
        return channelsAvailable
    }

    fun isLive(): Boolean {
        return channelsAvailable
    }

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(HealthMonitor::class.java)
    }
}
