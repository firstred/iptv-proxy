package io.github.firstred.iptvproxy.di.modules

import io.github.firstred.iptvproxy.config
import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.cache.*
import io.ktor.client.plugins.cache.storage.*
import io.ktor.client.plugins.compression.*
import io.ktor.client.plugins.logging.*
import io.ktor.http.*
import okhttp3.Dispatcher
import org.koin.core.qualifier.named
import org.koin.dsl.module
import java.io.File

val httpClientModule = module {
    // Client used for all other requests - referred to as `channels_*` in the configuration
    single<HttpClient> {
        HttpClient(OkHttp) {
            defaults()
            okHttpConfig(config.maxRequestsPerHost)

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

            expectSuccess = true
            followRedirects = true

            install(HttpCache) {
                publicStorage(FileStorage(File(config.getActualHttpCacheDirectory("icons"))))
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
}

fun HttpClientConfig<OkHttpConfig>.okHttpConfig(maxRequestsPerHost: Int = 6) {
    val okDispatcher = Dispatcher()
    okDispatcher.maxRequestsPerHost = maxRequestsPerHost

    engine {
        config {
            dispatcher(okDispatcher)
        }
        pipelining = true

        config.httpProxy?.let {
            proxy = ProxyBuilder.http(it)
        }
        configureProxyConnection()
    }
}

fun OkHttpConfig.configureProxyConnection() {
    config.socksProxy?.let {
        val regex = Regex("""socks[45]?://(?<host>.*?):(?<port>\\d+)""")
        val result = regex.find("${config.socksProxy}")

        if (result != null) {
            val host = result.groups["host"]?.value
            val port = result.groups["port"]?.value?.toInt() ?: -1

            proxy = ProxyBuilder.socks("$host", port)
        }
    }
}

fun HttpClientConfig<OkHttpConfig>.defaults() {
    install(Logging) {
        logger = Logger.DEFAULT
        level = LogLevel.HEADERS
        sanitizeHeader { header -> header == HttpHeaders.Authorization }
    }
    install(ContentEncoding) {
        deflate(.9f)
        gzip(.8f)
    }
}

fun HttpRequestRetryConfig.defaultRetryHandler(additionalConfig: HttpRequestRetryConfig.() -> Unit = {}) {
    retryOnExceptionIf { _, cause ->
        cause is ServerResponseException ||
                cause is ClientRequestException
                && listOf(HttpStatusCode.TooManyRequests.value, 456, 458)
            .contains(cause.response.status.value)
    }
    additionalConfig()
}
