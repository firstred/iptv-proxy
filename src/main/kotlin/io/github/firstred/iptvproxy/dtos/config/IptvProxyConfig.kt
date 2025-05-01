package io.github.firstred.iptvproxy.dtos.config

import io.github.firstred.iptvproxy.dtos.ForwardedHeaderValues
import io.github.firstred.iptvproxy.serialization.serializers.IntWithUnderscoreSerializer
import io.github.firstred.iptvproxy.utils.ensureTrailingSlash
import io.ktor.client.engine.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import java.io.File
import java.net.URI
import kotlin.time.Duration

@Serializable
data class IptvProxyConfig(
    val host: String = "::",
    @Serializable(with = IntWithUnderscoreSerializer::class) val port: Int = 8000,
    @Serializable(with = IntWithUnderscoreSerializer::class) val healthcheckPort: Int? = null, // 9090
    @Serializable(with = IntWithUnderscoreSerializer::class) val metricsPort: Int? = null,     // 9091
    val baseUrl: String? = null,
    val forwardedPass: String? = null,
    val appSecret: String = "ChangeMe!",
    val logLevel: String = "ERROR",
    val timeouts: IptvProxyConfigTimeouts = IptvProxyConfigTimeouts(),

    val database: IptvProxyDatabaseConfig = IptvProxyDatabaseConfig(),

    val clientHttpCache: IptvProxyClientHttpCacheConfig = IptvProxyClientHttpCacheConfig(),

    val clientConnectionMaxIdleSeconds: Int = 60,

    val updateInterval: Duration = Duration.parse("PT1H"),
    val updateIntervalOnFailure: Duration = Duration.parse("PT10M"),
    val cleanupInterval: Duration = Duration.parse("PT1H"),
    val schedulerThreadPoolSize: Int = 2,

    val servers: List<IptvServerConfig> = emptyList(),
    val users: List<IptvProxyUserConfig> = emptyList(),

    val sortChannels: Boolean = true,

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

    val sentry: IptvProxyConfigSentry? = null,
) {
    private fun getBaseCacheDirectory(): String {
        return checkDir(if (cacheDirectory.isNullOrEmpty()) {
            "${System.getProperty("java.io.tmpdir")}/iptvproxy"
        } else {
            cacheDirectory.removeSuffix("/") + "/iptvproxy"
        })
    }

    fun getMiscCacheDirectory(subdir: String? = null): String {
        return subdir?.let {
            checkDir(getBaseCacheDirectory() + "/cache")

            checkDir(getBaseCacheDirectory() + "/cache/$it")
        } ?: checkDir(getBaseCacheDirectory() + "/cache")
    }

    fun getActualHttpCacheDirectory(subdir: String? = null): String {
        return subdir?.let {
            checkDir(getBaseCacheDirectory() + "/http_cache")

            checkDir(getBaseCacheDirectory() + "/http_cache/$it")
        } ?: checkDir(getBaseCacheDirectory() + "/http_cache")
    }

    fun getActualDownloadDirectory(subdir: String? = null): String {
        return subdir?.let {
            checkDir(getBaseCacheDirectory() + "/download")

            checkDir(getBaseCacheDirectory() + "/download/$it")
        } ?: checkDir(getBaseCacheDirectory() + "/download")
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
    fun getConfiguredBaseUrl() = URI(baseUrl ?: "http://$host:$port").ensureTrailingSlash()
    fun getActualBaseUrl(request: RoutingRequest) = URI(getActualForwardedBaseUrl(request) ?: baseUrl ?: "http://$host:$port").ensureTrailingSlash()

    fun getActualHttpProxyURI(): URI? = httpProxy?.let { URI(it) }
    fun getActualHttpProxyConfiguration(): ProxyConfiguration? = httpProxy?.let {
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

            ProxyConfiguration(
                type = ProxyType.HTTP,
                host = host ?: "localhost",
                port = port ?: 80,
                username = username,
                password = password,
            )
        }
    }
    fun getActualSocksProxyConfiguration(): ProxyConfiguration? = socksProxy?.let {
        val regex = Regex("""^socks(4|4a|5)?://(?:(?<usernameorpassword>[^@:/]+)(?::(?<password>[^@/]*))?@)?(?<host>[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?(?:\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)*):(?<port>[0-9]{1,5})$""")
        val result = regex.find(it)

        if (result != null) {
            val host = result.groups["host"]?.value ?: ""
            val port = result.groups["port"]?.value?.toInt() ?: -1

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

            ProxyConfiguration(
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


