package io.github.firstred.iptvproxy.listeners

import io.github.firstred.iptvproxy.events.ChannelsAreAvailableEvent
import io.github.firstred.iptvproxy.listeners.hooks.HasOnApplicationEventHook
import io.github.firstred.iptvproxy.listeners.hooks.lifecycle.HasApplicationOnDatabaseInitializedHook
import org.koin.core.component.KoinComponent
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class HealthListener : KoinComponent, HasApplicationOnDatabaseInitializedHook, HasOnApplicationEventHook {
    private var channelsAvailable = false
    private var databaseReady = false

    fun isReady(): Boolean {
        return channelsAvailable && databaseReady
    }

    fun isLive(): Boolean {
        return channelsAvailable && databaseReady
    }

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(HealthListener::class.java)
    }


    override fun <T : Any> onApplicationEvent(event: T) {
        when (event) {
            is ChannelsAreAvailableEvent -> {
                channelsAvailable = true
            }
        }
    }

    override fun onApplicationDatabaseInitializedHook() {
        databaseReady = true
    }
}
