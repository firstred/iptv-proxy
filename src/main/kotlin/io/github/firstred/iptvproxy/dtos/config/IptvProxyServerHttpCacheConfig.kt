package io.github.firstred.iptvproxy.dtos.config

import kotlinx.serialization.Serializable

@Serializable
data class IptvProxyServerHttpCacheConfig(
    val enabled: Boolean = true,
    val duration: IptvProxyCacheDurationConfig = IptvProxyCacheDurationConfig(),
)
