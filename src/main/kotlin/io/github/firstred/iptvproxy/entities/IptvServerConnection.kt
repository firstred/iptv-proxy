package io.github.firstred.iptvproxy.entities

import io.github.firstred.iptvproxy.dtos.config.IptvFlatServerConfig
import io.ktor.client.*
import kotlinx.coroutines.sync.Semaphore
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.parameter.parametersOf
import org.koin.core.qualifier.named
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class IptvServerConnection(
    val config: IptvFlatServerConfig,
) : KoinComponent {
    private val semaphore: Semaphore = Semaphore(config.account.maxConcurrentRequests)

    val httpClient: HttpClient by inject(named(IptvServerConnection::class.java.simpleName)) { parametersOf(config) }

    suspend fun acquire() {
        semaphore.acquire()
    }

    fun tryAcquire(): Boolean {
        return semaphore.tryAcquire()
    }

    fun release() {
        semaphore.release()
    }

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(IptvServerConnection::class.java)
    }
}
