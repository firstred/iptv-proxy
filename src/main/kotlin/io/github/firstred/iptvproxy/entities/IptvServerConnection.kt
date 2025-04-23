package io.github.firstred.iptvproxy.entities

import io.github.firstred.iptvproxy.dtos.config.IptvFlatServerConfig
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import kotlinx.coroutines.sync.Semaphore
import org.koin.core.component.KoinComponent
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class IptvServerConnection(
    private val config: IptvFlatServerConfig,
) : KoinComponent {
    private val semaphore: Semaphore = Semaphore(config.account.maxUserConnections)

    private val httpClient: HttpClient

    init {
        httpClient = HttpClient(OkHttp)
    }

    fun acquire() = (semaphore::acquire)
    fun tryAcquire() = (semaphore::tryAcquire)()
    fun release() = (semaphore::release)

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(IptvServerConnection::class.java)
    }
}
