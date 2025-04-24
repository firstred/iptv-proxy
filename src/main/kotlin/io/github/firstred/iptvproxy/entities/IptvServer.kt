package io.github.firstred.iptvproxy.entities

import io.github.firstred.iptvproxy.dtos.config.IptvServerConfig
import kotlinx.coroutines.delay

class IptvServer(
    val name: String,
    val config: IptvServerConfig,
    private val connections: MutableList<IptvServerConnection>,
) {
    suspend fun withConnection(
        action: suspend (connection: IptvServerConnection) -> Unit
    ) {
        val connection = acquire()
        try {
            action(connection)
        } finally {
            connection.release()
        }
    }

    private suspend fun acquire(): IptvServerConnection {
        do {
            tryAcquire()?.also { return it }
            LOG.info("try acquire server connection")
            delay(100L)
        } while (true)
    }

    private fun tryAcquire(): IptvServerConnection? {
        synchronized (connections) {
            for (serverConnection in connections.shuffled()) {
                if (serverConnection.tryAcquire()) return serverConnection
            }
        }

        return null
    }

    companion object {
        private val LOG = org.slf4j.LoggerFactory.getLogger(IptvServer::class.java)
    }
}
