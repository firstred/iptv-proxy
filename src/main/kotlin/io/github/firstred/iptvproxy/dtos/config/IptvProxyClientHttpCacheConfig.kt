package io.github.firstred.iptvproxy.dtos.config

import kotlinx.serialization.Serializable
import kotlin.time.Duration

@Serializable
data class IptvProxyClientHttpCacheConfig(
    val enabled: Boolean = false,
    val cleanupInterval: Duration = Duration.parse("PT10M"),
    val ttl: IptvProxyClientHttpCacheTtlConfig = IptvProxyClientHttpCacheTtlConfig(),
)

