package io.github.firstred.iptvproxy.dtos.config

import kotlinx.serialization.Serializable

@Serializable
data class IptvProxyCacheSizeConfig(
    val videoChunks: ULong = 500UL * 1024UL * 1024UL,
    val movieInfo: ULong = 50UL * 1024UL * 1024UL,
    val seriesInfo: ULong = 50UL * 1024UL * 1024UL,
)
