package io.github.firstred.iptvproxy.dtos.config

import io.github.firstred.iptvproxy.dtos.ForwardedHeaderValues
import io.github.firstred.iptvproxy.serialization.serializers.UIntWithUnderscoreSerializer
import io.github.firstred.iptvproxy.utils.ensureTrailingSlash
import io.github.firstred.iptvproxy.utils.toEncodedJavaURI
import io.ktor.client.engine.*
import io.ktor.http.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import java.io.File
import java.net.URI
import kotlin.time.Duration

@Serializable
data class IptvProxyConfig(
    val host: String = "::",
    @Serializable(with = UIntWithUnderscoreSerializer::class) val port: UInt = 8000u,
    @Serializable(with = UIntWithUnderscoreSerializer::class) val healthcheckPort: UInt? = null, // 9090
    @Serializable(with = UIntWithUnderscoreSerializer::class) val metricsPort: UInt? = null,     // 9091
    val baseUrl: String? = null,
    val forwardedPass: String? = null,
    val appSecret: String = "ChangeMe!",
    val logLevel: String = "ERROR",
    val timeouts: IptvProxyConfigTimeouts = IptvProxyConfigTimeouts(),

    val database: IptvProxyDatabaseConfig = IptvProxyDatabaseConfig(),

    val cache: IptvProxyCacheConfig = IptvProxyCacheConfig(),

    @Serializable(with = UIntWithUnderscoreSerializer::class) val clientConnectionMaxIdleSeconds: UInt = 60u,

    val updateInterval: Duration = Duration.parse("PT4H"),
    val updateIntervalOnFailure: Duration = Duration.parse("PT10M"),
    val cleanupInterval: Duration = Duration.parse("PT6H"),
    val channelMaxStalePeriod: Duration = Duration.parse("P7D"),
    @Serializable(with = UIntWithUnderscoreSerializer::class) val schedulerThreadPoolSize: UInt = 2u,

    val servers: List<IptvServerConfig> = emptyList(),
    val users: List<IptvProxyUserConfig> = emptyList(),

    val sortChannelsByName: Boolean = true,
    val trimEpg: Boolean = true,

    val socksProxy: String? = null, // socks5://<username>:<password>@server_host:port
    val httpProxy: String? = null, // http://<username>:<password>@server_host:port

    val cacheDirectory: String? = System.getProperty("java.io.tmpdir"),

    val compressResponses: Boolean = true,

    val cors: IptvProxyCorsConfig = IptvProxyCorsConfig(),

    val whitelistIptvClientHeaders: List<String> = listOf("*"),
    val blacklistIptvClientHeaders: List<String> = listOf(),
    val whitelistIptvServerHeaders: List<String> = listOf("*"),
    val blacklistIptvServerHeaders: List<String> = listOf(),

    val gracefulShutdownPeriod: Duration = Duration.parse("PT30S"),

    val sentry: IptvProxySentryConfig? = null,
) {
    private fun getBaseCacheDirectory(): String {
        return checkDir(if (cacheDirectory.isNullOrEmpty()) {
            "${System.getProperty("java.io.tmpdir")}/iptvproxy"
        } else {
            cacheDirectory.removeSuffix("/") + "/iptvproxy"
        })
    }

    fun getCacheDirectory(subdir: String? = null): String {
        return subdir?.let {
            checkDir(getBaseCacheDirectory() + "/cache")

            checkDir(getBaseCacheDirectory() + "/cache/$it")
        } ?: checkDir(getBaseCacheDirectory() + "/cache")
    }

    fun getHttpCacheDirectory(subdir: String? = null): String {
        return subdir?.let {
            checkDir(getBaseCacheDirectory() + "/http_cache")

            checkDir(getBaseCacheDirectory() + "/http_cache/$it")
        } ?: checkDir(getBaseCacheDirectory() + "/http_cache")
    }

    fun getLargeFileSinkDirectory(subdir: String? = null): String {
        return subdir?.let {
            checkDir(getBaseCacheDirectory() + "/http_sink")

            checkDir(getBaseCacheDirectory() + "/http_sink/$it")
        } ?: checkDir(getBaseCacheDirectory() + "/http_sink")
    }

    fun getForwardedValues(forwardedHeadersContentList: List<String>?): ForwardedHeaderValues {
        if (null == forwardedHeadersContentList) return ForwardedHeaderValues()
        val forwardedHeaderContent = forwardedHeadersContentList.joinToString(",")

        val forwardedPassword = forwardedHeaderContent.split(';', ',').map { it.trim(' ') }.find { it.startsWith("pass=") }?.substringAfter("=")
        if (forwardedPassword != forwardedPass) return ForwardedHeaderValues()

        val forwardedBaseUrl = forwardedHeaderContent.split(';', ',').map { it.trim(' ') }.find { it.startsWith("baseUrl=") }?.substringAfter("=")
        val forwardedIptvProxyUser = forwardedHeaderContent.split(';', ',').map { it.trim(' ') }.find { it.startsWith("proxyUser=") }?.substringAfter("=")

        return ForwardedHeaderValues(
            baseUrl = forwardedBaseUrl,
            proxyUser = forwardedIptvProxyUser,
        )
    }

    fun getActualForwardedBaseUrl(request: RoutingRequest): String? = getForwardedValues(request.headers.getAll("Forwarded")).baseUrl
    fun getConfiguredBaseUrl() = Url(baseUrl ?: "http://$host:$port").toEncodedJavaURI().ensureTrailingSlash()
    fun getActualBaseUrl(request: RoutingRequest) = Url(getActualForwardedBaseUrl(request) ?: baseUrl ?: "http://$host:$port").toEncodedJavaURI().ensureTrailingSlash()

    fun getActualHttpProxyURI(): URI? = httpProxy?.let { Url(it).toEncodedJavaURI() }
    fun getActualHttpProxyConfiguration(): IptvConnectionProxyConfig? = httpProxy?.let {
        getActualHttpProxyURI().let { uri ->
            val host = uri?.host
            val port = uri?.port

            var username: String? = null
            var password: String? = null
            uri?.userInfo?.let {
                val userInfo = it.split(":")
                username = userInfo[0]
                password = if (userInfo.size > 1) userInfo[1] else null
            }

            IptvConnectionProxyConfig(
                type = ProxyType.HTTP,
                host = host ?: "localhost",
                port = port?.toUInt() ?: 80u,
                username = username,
                password = password,
            )
        }
    }
    fun getActualSocksProxyConfiguration(): IptvConnectionProxyConfig? = socksProxy?.let {
        val regex = Regex("""^socks(4|4a|5)?://(?:(?<usernameorpassword>[^@:/]+)(?::(?<password>[^@/]*))?@)?(?<host>[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?(?:\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)*):(?<port>[0-9]{1,5})$""")
        val result = regex.find(it)

        if (result != null) {
            val host = result.groups["host"]?.value ?: throw IllegalArgumentException("Invalid socks proxy host")
            val port = result.groups["port"]?.value?.toUInt() ?: throw IllegalArgumentException("Invalid sockx proxy port")

            var username: String? = null
            var password: String? = null
            result.groups["password"]?.let {
                username = result.groups["usernameorpassword"]?.value
                password = it.value
            }
                ?: result.groups["usernameorpassword"]?.let {
                    username = "nobody"
                    password = it.value
                }

            IptvConnectionProxyConfig(
                type = ProxyType.SOCKS,
                host = host,
                port = port,
                username = username,
                password = password,
            )
        } else {
            null
        }
    }

    fun getServerConfigByName(name: String): IptvServerConfig? = servers.find { it.name == name }

    companion object {
        private val checkedDirs = mutableSetOf<String>()

        private fun checkDir(dir: String): String {
            if (dir in checkedDirs) return dir

            // If the directory does not exist, try to create it first
            File(dir).apply { if (!exists()) mkdirs() }

            checkedDirs.add(dir)

            return dir
        }
    }
}


