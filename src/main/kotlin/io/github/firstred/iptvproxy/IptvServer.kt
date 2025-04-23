package io.github.firstred.iptvproxy

import io.github.firstred.iptvproxy.dtos.config.IptvServerConfig
import kotlinx.coroutines.delay

class IptvServer(
    val name: String,
    val config: IptvServerConfig,
    private val connections: MutableList<IptvServerConnection>,
) {
    suspend fun acquire(): IptvServerConnection {
        var serverConnection: IptvServerConnection? = null
        while (null == serverConnection) {
            serverConnection = tryAcquire()
            if (null == serverConnection) delay(100L)
        }

        return serverConnection
    }

    fun tryAcquire(): IptvServerConnection? = synchronized(connections) {
        TODO()
//        return runBlocking {
//            for (serverConnection in connections.shuffled()) {
//                if (serverConnection.tryAcquire()) return@runBlocking serverConnection
//            }
//
//            return@runBlocking null
//        }
    }
}
