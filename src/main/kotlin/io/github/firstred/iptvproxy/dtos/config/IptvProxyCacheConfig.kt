package io.github.firstred.iptvproxy.dtos.config

import kotlinx.serialization.Serializable

@Serializable
data class IptvProxyCacheConfig(
    val enabled: Boolean = true,
    val ttl: IptvProxyCacheDurationConfig = IptvProxyCacheDurationConfig(),
    val size: IptvProxyCacheSizeConfig = IptvProxyCacheSizeConfig(),
)
