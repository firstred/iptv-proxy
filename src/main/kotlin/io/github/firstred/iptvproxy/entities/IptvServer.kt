package io.github.firstred.iptvproxy.entities

import io.github.firstred.iptvproxy.dtos.config.IptvServerAccountConfig
import io.github.firstred.iptvproxy.dtos.config.IptvServerConfig
import kotlinx.coroutines.delay

class IptvServer(
    val name: String,
    val config: IptvServerConfig,
    private val connections: MutableList<IptvServerConnection>,
) {
    suspend fun withConnection(
        specificAccount: IptvServerAccountConfig? = null,
        action: suspend (connection: IptvServerConnection, releaseConnectionEarly: () -> Unit) -> Unit,
    ) {
        var released = false
        val connection = acquire(specificAccount)
        fun releaseEarly() {
            connection.release()
            released = true
        }

        try {
            action(connection, ::releaseEarly)
        } finally {
            if (!released) connection.release()
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
