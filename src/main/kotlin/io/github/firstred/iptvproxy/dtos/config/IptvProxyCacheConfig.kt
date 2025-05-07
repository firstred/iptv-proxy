package io.github.firstred.iptvproxy.dtos.config

import kotlinx.serialization.Serializable

@Serializable
data class IptvProxyCacheConfig(
    val enabled: Boolean = false,
    val ttl: IptvProxyCacheDurationConfig = IptvProxyCacheDurationConfig(),
    val size: IptvProxyCacheSizeConfig = IptvProxyCacheSizeConfig(),
)
