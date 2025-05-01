package io.github.firstred.iptvproxy.entities

import io.github.firstred.iptvproxy.dtos.config.IptvServerAccountConfig
import io.github.firstred.iptvproxy.dtos.config.IptvServerConfig
import kotlinx.coroutines.delay
import java.util.*
import kotlin.concurrent.timer

class IptvServer(
    val name: String,
    val config: IptvServerConfig,
    private val connections: MutableList<IptvServerConnection>,
) {
    suspend fun withConnection(
        totalTimoutInMilliseconds: Long,
        specificAccount: IptvServerAccountConfig? = null,
        action: suspend (connection: IptvServerConnection, releaseConnectionEarly: () -> Unit) -> Unit,
    ) {
        var released = false
        val connection = acquire(specificAccount)
        var connectionTimer: Timer? = null

        fun releaseConnection() {
            if (released) return
            connection.release()
            released = true
            connectionTimer?.cancel()
            connectionTimer = null
        }

        @Suppress("AssignedValueIsNeverRead")
        connectionTimer = timer(initialDelay = totalTimoutInMilliseconds, period = Long.MAX_VALUE, daemon = true) {
            releaseConnection()
        }

        try {
            action(connection, ::releaseConnection)
        } finally {
            if (!released) releaseConnection()
        }
    }

    private suspend fun acquire(specificAccount: IptvServerAccountConfig? = null): IptvServerConnection {
        do {
            tryAcquire(specificAccount)?.also { return it }
            LOG.info("Trying to acquire server connection")
            delay(100L)
        } while (true)
    }

    private fun tryAcquire(specificAccount: IptvServerAccountConfig? = null): IptvServerConnection? {
        for (serverConnection in connections.filter { null == specificAccount || it.config.account == specificAccount }.shuffled()) {
            if (serverConnection.tryAcquire()) return serverConnection
        }

        return null
    }

    companion object {
        private val LOG = org.slf4j.LoggerFactory.getLogger(IptvServer::class.java)
    }
}
