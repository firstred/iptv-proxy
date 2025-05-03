package io.github.firstred.iptvproxy.dtos.config

import kotlinx.serialization.Serializable

@Serializable
data class IptvProxyClientHttpCacheConfig(
    val enabled: Boolean = false,
    val ttl: IptvProxyClientHttpCacheTtlConfig = IptvProxyClientHttpCacheTtlConfig(),
)

