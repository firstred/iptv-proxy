package io.github.firstred.iptvproxy.listeners

import io.github.firstred.iptvproxy.listeners.hooks.lifecycle.HasApplicationOnTerminateHook
import io.github.firstred.iptvproxy.plugins.dataSource
import org.koin.core.component.KoinComponent
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class DatabaseListener : KoinComponent, HasApplicationOnTerminateHook {
    override fun onApplicationTerminateHook() {
        try {
            LOG.info("Closing database connection")
            dataSource.close()
            LOG.info("Database connection closed")
        } catch (_: Throwable) {
        }
    }

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(DatabaseListener::class.java)
    }
}
