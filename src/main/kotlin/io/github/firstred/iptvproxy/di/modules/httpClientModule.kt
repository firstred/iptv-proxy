package io.github.firstred.iptvproxy.di.modules

import io.github.firstred.iptvproxy.classes.IptvServerConnection
import io.github.firstred.iptvproxy.config
import io.github.firstred.iptvproxy.di.hooksOf
import io.github.firstred.iptvproxy.dtos.config.IptvFlatServerConfig
import io.github.firstred.iptvproxy.managers.CacheManager
import io.github.firstred.iptvproxy.serialization.json
import io.github.firstred.iptvproxy.utils.defaultMaxConnections
import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.cache.*
import io.ktor.client.plugins.cache.storage.*
import io.ktor.client.plugins.compression.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.io.IOException
import okhttp3.Dispatcher
import org.koin.core.qualifier.named
import org.koin.dsl.binds
import org.koin.dsl.module
import java.io.File
import java.net.URI

val httpClientModule = module {
    single { CacheManager() } binds hooksOf(CacheManager::class)

    // Client used for all other requests - referred to as `channels_*` in the configuration
    single<HttpClient> {
        HttpClient(OkHttp) {
            defaults()
            okHttpConfig()

            expectSuccess = true
            followRedirects = true

            install(HttpRequestRetry) {
                maxRetries = 5
                defaultRetryHandler {
                    delayMillis { config.timeouts.playlist.retryDelayMilliseconds.toLong() }
                }
            }
            install(HttpTimeout) {
                connectTimeoutMillis = config.timeouts.playlist.connectMilliseconds.toLong()
                socketTimeoutMillis = config.timeouts.playlist.connectMilliseconds.toLong()
                requestTimeoutMillis = config.timeouts.playlist.totalMilliseconds.toLong()
            }
        }
    }

    // Client used to retrieve icons
    single<HttpClient>(named("images")) {
        HttpClient(OkHttp) {
            defaults()
            okHttpConfig(maxRequestsPerHost = 64)

            expectSuccess = false
            followRedirects = true

            if (config.cache.enabled) install(HttpCache) {
                publicStorage(FileStorage(File(config.getHttpCacheDirectory("images"))))
            }
            install(HttpRequestRetry) {
                maxRetries = 5
                defaultRetryHandler {
                    delayMillis { config.timeouts.icon.retryDelayMilliseconds.toLong() }
                }
            }
            install(HttpTimeout) {
                connectTimeoutMillis = config.timeouts.icon.connectMilliseconds.toLong()
                socketTimeoutMillis = config.timeouts.icon.connectMilliseconds.toLong()
                requestTimeoutMillis = config.timeouts.icon.totalMilliseconds.toLong()
            }
        }
    }

    // Factory for iptv server connections
    factory<HttpClient>(named(IptvServerConnection::class.java.simpleName)) {(flatServerConfig: IptvFlatServerConfig) ->
        HttpClient(OkHttp) {
            defaults()
            val okDispatcher = Dispatcher()
            okDispatcher.maxRequestsPerHost = flatServerConfig.account!!.maxConcurrentRequestsPerHost.toInt()

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
                maxRetries = 5
                defaultRetryHandler {
                    delayMillis { flatServerConfig.timeouts.retryDelayMilliseconds.toLong() }
                }
            }
            install(HttpTimeout) {
                connectTimeoutMillis = flatServerConfig.timeouts.connectMilliseconds.toLong()
                socketTimeoutMillis = flatServerConfig.timeouts.connectMilliseconds.toLong()
                requestTimeoutMillis = flatServerConfig.timeouts.totalMilliseconds.toLong()
            }
        }
    }
}

// OkHttp is the engine used for HTTP Client - this is the specific OkHttp engine configuration
fun HttpClientConfig<OkHttpConfig>.okHttpConfig(maxRequestsPerHost: Int = defaultMaxConnections.toInt()) {
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
        proxy = ProxyBuilder.socks(host, port.toInt())
    }
}

// OkHttp is the engine used for HTTP Client - this is the default configuration
fun HttpClientConfig<OkHttpConfig>.defaults() {
    install(Logging) {
        logger = Logger.DEFAULT  // Default logger for JVM
        level = if (arrayOf("debug", "trace", "info").contains((System.getProperty("log.level") ?: "").lowercase())) LogLevel.HEADERS else LogLevel.NONE // Log headers only

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
                    HttpStatusCode.UnprocessableEntity.value,
                    HttpStatusCode.ExpectationFailed.value,
                    HttpStatusCode.FailedDependency.value,
                    HttpStatusCode.NotAcceptable.value,
                    HttpStatusCode.Locked.value,
                    HttpStatusCode.Forbidden.value,    // We'll assume that the account is correctly configured
                    HttpStatusCode.Unauthorized.value, // We'll assume that the account is correctly configured
                    HttpStatusCode.NotFound.value,
                    HttpStatusCode.PaymentRequired.value,
                    HttpStatusCode.BadRequest.value,
                    456,
                    458
                ).contains(cause.response.status.value)
    }
    additionalConfig()
}
