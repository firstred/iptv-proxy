package io.github.firstred.iptvproxy.entities

import io.github.firstred.iptvproxy.di.modules.defaults
import io.github.firstred.iptvproxy.di.modules.okHttpConfig
import io.github.firstred.iptvproxy.dtos.config.IptvFlatServerConfig
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import kotlinx.coroutines.sync.Semaphore
import org.koin.core.component.KoinComponent
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import io.github.firstred.iptvproxy.di.modules.defaultRetryHandler
import io.ktor.client.plugins.*

class IptvServerConnection(
    private val config: IptvFlatServerConfig,
) : KoinComponent {
    private val semaphore: Semaphore = Semaphore(config.account.maxConcurrentRequests)

    val httpClient: HttpClient = HttpClient(OkHttp) {
        defaults()
        okHttpConfig(config.account.maxConcurrentRequestsPerHost)

        install(HttpRequestRetry) {
            defaultRetryHandler {
                delayMillis { config.timeouts.playlist.retryDelayMilliseconds }
            }
        }
        install(HttpTimeout) {
            connectTimeoutMillis = config.timeouts.playlist.connectMilliseconds
            socketTimeoutMillis = config.timeouts.playlist.connectMilliseconds
            requestTimeoutMillis = config.timeouts.playlist.totalMilliseconds
        }
    }

    fun acquire() = (semaphore::acquire)
    fun tryAcquire() = (semaphore::tryAcquire)()
    fun release() = (semaphore::release)

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(IptvServerConnection::class.java)
    }
}
