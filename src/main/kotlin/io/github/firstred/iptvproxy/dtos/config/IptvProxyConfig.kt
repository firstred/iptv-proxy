package io.github.firstred.iptvproxy.dtos.config

import io.github.firstred.iptvproxy.serialization.serializers.IntWithUnderscoreSerializer
import io.github.firstred.iptvproxy.utils.defaultMaxConnections
import kotlinx.serialization.Serializable
import java.io.File
import kotlin.time.Duration

@Serializable
data class IptvProxyConfig(
    val host: String = "::",
    @Serializable(with = IntWithUnderscoreSerializer::class) val port: Int = 8000,
    @Serializable(with = IntWithUnderscoreSerializer::class) val healthcheckPort: Int? = null, // 9090
    @Serializable(with = IntWithUnderscoreSerializer::class) val metricsPort: Int? = null,     // 9091
    val baseUrl: String = "${host}:${port}",
    val appSecret: String = "ChangeMe!",
    val logLevel: String = "ERROR",
    val timeouts: IptvProxyConfigTimeouts = IptvProxyConfigTimeouts(),

    val maxRequestsPerHost: Int = defaultMaxConnections,
    val clientConnectionMaxIdleSeconds: Int = 60,

    val updateInterval: Duration = Duration.parse("PT1H"),
    val schedulerThreadPoolSize: Int = 2,

    val servers: List<IptvServerConfig> = emptyList(),
    val users: List<IptvProxyUserConfig> = emptyList(),

    val sortChannels: Boolean = true,

    val socksProxy: String? = null, // socks5://<username>:<password>@server_host:port
    val httpProxy: String? = null, // http://<username>:<password>@server_host:port

    val tmpDirectory: String? = System.getProperty("java.io.tmpdir"),

    val cors: IptvProxyCorsConfig = IptvProxyCorsConfig(),

    val sentry: IptvProxyConfigSentry? = null,
) {
    private fun getTempDirectory(): String {
        return checkDir(if (tmpDirectory.isNullOrEmpty()) {
            "${System.getProperty("java.io.tmpdir")}/iptvproxy"
        } else {
            tmpDirectory.removeSuffix("/") + "/iptvproxy"
        })
    }

//    fun setDefaults(): IptvProxyConfig {
//        servers.forEach { server ->
//            server.accounts?.forEachIndexed { idx, account ->
//                if (account.idx < 0) account.idx = idx
//            }
//        }
//
//        return this
//    }

    fun getCacheDirectory(subdir: String? = null): String {
        return subdir?.let {
            checkDir(getTempDirectory() + "/cache")

            checkDir(getTempDirectory() + "/cache/$it")
        } ?: checkDir(getTempDirectory() + "/cache")
    }

    fun getHttpCacheDirectory(subdir: String? = null): String {
        return subdir?.let {
            checkDir(getTempDirectory() + "/http_cache")

            checkDir(getTempDirectory() + "/http_cache/$it")
        } ?: checkDir(getTempDirectory() + "/http_cache")
    }

    fun getDownloadDirectory(subdir: String? = null): String {
        return subdir?.let {
            checkDir(getTempDirectory() + "/download")

            checkDir(getTempDirectory() + "/download/$it")
        } ?: checkDir(getTempDirectory() + "/download")
    }

    companion object {
        private val checkedDirs = mutableSetOf<String>()

        fun checkDir(dir: String): String {
            if (dir in checkedDirs) return dir

            // If the directory does not exist, try to create it first
            File(dir).apply { if (!exists()) mkdirs() }

            checkedDirs.add(dir)

            return dir
        }
    }
}
