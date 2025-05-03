package io.github.firstred.iptvproxy.dtos.config

import kotlinx.serialization.Serializable

@Serializable
data class IptvProxyCacheSizeConfig(
    val videoChunks: Long = 500 * 1024 * 1024L,
    val movieInfo: Long = 50 * 1024 * 1024L,
    val seriesInfo: Long = 50 * 1024 * 1024L,
)
