package io.github.firstred.iptvproxy

import io.github.firstred.iptvproxy.config.IptvConnectionConfig
import io.github.firstred.iptvproxy.config.IptvServerConfig
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.util.*

class IptvServer(sc: IptvServerConfig, cc: IptvConnectionConfig, httpClient: HttpClient) {
    private val sc: IptvServerConfig = Objects.requireNonNull(sc)
    private val cc: IptvConnectionConfig = Objects.requireNonNull(cc)

    val httpClient: HttpClient = Objects.requireNonNull(httpClient)

    private var acquired = 0

    val name: String?
        get() = sc.name

    val url: String?
        get() = cc.url

    val sendUser: Boolean
        get() = sc.sendUser

    val proxyStream: Boolean
        get() = sc.proxyStream

    val channelFailedMs: Long
        get() = sc.channelFailedMs

    val infoTimeoutMs: Long
        get() = sc.infoTimeoutMs

    val infoTotalTimeoutMs: Long
        get() = sc.infoTotalTimeoutMs

    val infoRetryDelayMs: Long
        get() = sc.infoRetryDelayMs

    val catchupTimeoutMs: Long
        get() = sc.catchupTimeoutMs

    val catchupTotalTimeoutMs: Long
        get() = sc.catchupTotalTimeoutMs

    val catchupRetryDelayMs: Long
        get() = sc.catchupRetryDelayMs

    val streamStartTimeoutMs: Long
        get() = sc.streamStartTimeoutMs

    val streamReadTimeoutMs: Long
        get() = sc.streamReadTimeoutMs

    @Synchronized
    fun acquire(): Boolean {
        if (acquired >= cc.maxConnections) {
            return false
        }

        acquired++
        return true
    }

    @Synchronized
    fun release() {
        if (acquired > 0) {
            acquired--
        }
    }

    fun createRequest(url: String): HttpRequest.Builder {
        val builder = HttpRequest.newBuilder()
            .uri(URI.create(url))

        // add basic authentication
        if (cc.login != null && cc.password != null) {
            builder.header(
                "Authorization",
                "Basic " + Base64.getEncoder().encodeToString((cc.login + ":" + cc.password).toByteArray())
            )
        }

        return builder
    }

    companion object {
        const val PROXY_USER_HEADER: String = "iptv-proxy-user"
    }
}
