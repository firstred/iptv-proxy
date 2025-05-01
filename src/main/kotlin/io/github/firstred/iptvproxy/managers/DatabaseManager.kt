package io.github.firstred.iptvproxy.managers

import io.github.firstred.iptvproxy.config
import io.github.firstred.iptvproxy.listeners.hooks.lifecycle.HasApplicationOnTerminateHook
import io.github.firstred.iptvproxy.plugins.dataSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.koin.core.component.KoinComponent
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class DatabaseManager : KoinComponent, HasApplicationOnTerminateHook {
    override fun onApplicationTerminateHook() {
        LOG.info("Closing database connection")
        runBlocking {
            // Trigger in the last 20 seconds of the graceful shutdown period
            delay((config.gracefulShutdownPeriod.inWholeMilliseconds - 20000).coerceAtLeast(0))
            dataSource.close()
        }
        LOG.info("Database connection closed")
    }

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(DatabaseManager::class.java)
    }
}
