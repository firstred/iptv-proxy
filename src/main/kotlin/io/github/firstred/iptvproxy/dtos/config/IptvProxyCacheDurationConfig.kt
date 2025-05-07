package io.github.firstred.iptvproxy.dtos.config

import kotlinx.serialization.Serializable
import kotlin.time.Duration

@Serializable
data class IptvProxyCacheDurationConfig(
    val videoChunks: Duration = Duration.parse("PT2M"),
    val movieInfo: Duration = Duration.parse("P1D"),
    val seriesInfo: Duration = Duration.parse("PT1H"),
    val icons: Duration = Duration.Companion.parse("P30D"),
)
