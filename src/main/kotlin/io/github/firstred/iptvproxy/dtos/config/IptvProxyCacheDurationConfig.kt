package io.github.firstred.iptvproxy.dtos.config

import kotlinx.serialization.Serializable
import kotlin.time.Duration

@Serializable
data class IptvProxyCacheDurationConfig(
    val videoChunk: Duration = Duration.parse("PT2M"),
    val videoInfo: Duration = Duration.parse("P1D"),
    val seriesInfo: Duration = Duration.parse("PT1H"),
)
