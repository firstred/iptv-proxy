package io.github.firstred.iptvproxy.entities

import io.github.firstred.iptvproxy.dtos.config.IptvServerConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

class IptvServer(
    val name: String,
    val config: IptvServerConfig,
    private val connections: MutableList<IptvServerConnection>,
) {
    suspend fun withConnection(action: suspend () -> Unit) {
        val connection = acquire()
        try {
            action()
        } finally {
            connection.release()
        }
    }

    private suspend fun getConnection(): IptvServerConnection {
        var serverConnection: IptvServerConnection? = null
        while (null == serverConnection) {
            serverConnection = tryAcquire()
            if (null == serverConnection) delay(100L)
        }

        return serverConnection
    }

    private fun acquire(): IptvServerConnection = synchronized(connections) {
        return runBlocking {
            for (serverConnection in connections.shuffled()) {
                serverConnection.acquire()
                return@runBlocking serverConnection
            }

            throw IllegalStateException("No available server connection")
        }
    }

    private fun tryAcquire(): IptvServerConnection? = synchronized(connections) {
        return runBlocking {
            for (serverConnection in connections.shuffled()) {
                if (serverConnection.tryAcquire()) return@runBlocking serverConnection
            }

            return@runBlocking null
        }
    }
}
