package io.github.firstred.iptvproxy.di.modules

import io.github.firstred.iptvproxy.config
import io.github.firstred.iptvproxy.di.hooksOf
import io.github.firstred.iptvproxy.dtos.config.IptvFlatServerConfig
import io.github.firstred.iptvproxy.entities.IptvServerConnection
import io.github.firstred.iptvproxy.managers.HttpCacheManager
import io.github.firstred.iptvproxy.plugins.ktor.client.ProxyFileStorage
import io.github.firstred.iptvproxy.serialization.json
import io.github.firstred.iptvproxy.utils.defaultMaxConnections
import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.cache.*
import io.ktor.client.plugins.compression.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.io.IOException
import okhttp3.Dispatcher
import org.koin.core.qualifier.named
import org.koin.dsl.binds
import org.koin.dsl.module
import java.io.File
import java.net.URI
import java.util.*

val httpClientModule = module {
    single { HttpCacheManager() } binds hooksOf(HttpCacheManager::class)

    // Client used for all other requests - referred to as `channels_*` in the configuration
    single<HttpClient> {
        HttpClient(OkHttp) {
            defaults()
            okHttpConfig()

            expectSuccess = true
            followRedirects = true

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
    }

    // Client used to retrieve icons
    single<HttpClient>(named("icon")) {
        HttpClient(OkHttp) {
            defaults()
            okHttpConfig(maxRequestsPerHost = 64)

            expectSuccess = false
            followRedirects = true

            if (config.clientHttpCache.enabled) install(HttpCache) {
                publicStorage(ProxyFileStorage(File(config.getActualHttpCacheDirectory("images"))))
            }
            install(HttpRequestRetry) {
                defaultRetryHandler {
                    delayMillis { config.timeouts.icon.retryDelayMilliseconds }
                }
            }
            install(HttpTimeout) {
                connectTimeoutMillis = config.timeouts.icon.connectMilliseconds
                socketTimeoutMillis = config.timeouts.icon.connectMilliseconds
                requestTimeoutMillis = config.timeouts.icon.totalMilliseconds
            }
        }
    }

    // Factory for iptv server connections
    factory<HttpClient>(named(IptvServerConnection::class.java.simpleName)) {(flatServerConfig: IptvFlatServerConfig) ->
        HttpClient(OkHttp) {
            defaults()
            val okDispatcher = Dispatcher()
            okDispatcher.maxRequestsPerHost = flatServerConfig.account.maxConcurrentRequestsPerHost

            engine {
                config {
                    followRedirects(false)
                    followSslRedirects(false)
                    dispatcher(okDispatcher)
                }
                pipelining = true

                configureProxyConnection()
            }

            followRedirects = false

            install(HttpRequestRetry) {
                defaultRetryHandler {
                    delayMillis { flatServerConfig.timeouts.playlist.retryDelayMilliseconds }
                }
            }
            install(HttpTimeout) {
                connectTimeoutMillis = flatServerConfig.timeouts.playlist.connectMilliseconds
                socketTimeoutMillis = flatServerConfig.timeouts.playlist.connectMilliseconds
                requestTimeoutMillis = flatServerConfig.timeouts.playlist.totalMilliseconds
            }
        }
    }
}

// OkHttp is the engine used for HTTP Client - this is the specific OkHttp engine configuration
fun HttpClientConfig<OkHttpConfig>.okHttpConfig(maxRequestsPerHost: Int = defaultMaxConnections) {
    val okDispatcher = Dispatcher()
    okDispatcher.maxRequestsPerHost = maxRequestsPerHost

    engine {
        config {
            dispatcher(okDispatcher)
        }
        pipelining = true


        configureProxyConnection()
    }
}

fun OkHttpConfig.configureProxyConnection() {
    // Proxy configuration without authentication
    config.httpProxy?.let {
        val proxyUri = config.getActualHttpProxyURI()!!

        proxy = ProxyBuilder.http(
            URI(
                proxyUri.scheme,
                null,
                proxyUri.host,
                proxyUri.port,
                proxyUri.path,
                proxyUri.query,
                proxyUri.fragment,
            ).toString()
        )
    }
    config.socksProxy?.let {
        val (_, host, port) = config.getActualSocksProxyConfiguration()!!
        proxy = ProxyBuilder.socks(host, port)
    }
}

// OkHttp is the engine used for HTTP Client - this is the default configuration
fun HttpClientConfig<OkHttpConfig>.defaults() {
    defaultRequest {
        // Handle proxy authentication part of the configuration
        config.httpProxy?.let {
            val (_, _, _, username, password) = config.getActualHttpProxyConfiguration()!!
            val credentials = Base64.getEncoder().encodeToString("$username:$password".toByteArray())
            header(HttpHeaders.ProxyAuthorization, "Basic $credentials")
        }
    }
    install(Logging) {
        logger = Logger.DEFAULT  // Default logger for JVM
        level = LogLevel.HEADERS // Log headers only

        // Hide authorization header from logs
        sanitizeHeader { header -> header == HttpHeaders.Authorization }
    }
    install(ContentNegotiation) { json(json) }
    install(ContentEncoding) {
        deflate(.9f)
        gzip(.8f)
    }
}

// This is the default retry handler shared by all clients
fun HttpRequestRetryConfig.defaultRetryHandler(additionalConfig: HttpRequestRetryConfig.() -> Unit = {}) {
    retryOnExceptionIf { _, cause ->
                cause is IOException                // Handle network errors and timeouts
                || cause is ServerResponseException // Handle 5xx server errors
                || cause is ClientRequestException  // Handle specific client errors
                && listOf(
                    HttpStatusCode.PayloadTooLarge.value,
                    HttpStatusCode.RequestTimeout.value,
                    HttpStatusCode.Conflict.value,
                    HttpStatusCode.Gone.value,
                    HttpStatusCode.TooEarly.value,
                    HttpStatusCode.TooManyRequests.value,
                    456,
                    458
                ).contains(cause.response.status.value)
    }
    additionalConfig()
}
