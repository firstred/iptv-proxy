package io.github.firstred.iptvproxy.dtos.config

import kotlinx.serialization.Serializable
import kotlin.time.Duration

@Serializable
data class IptvProxyClientHttpCacheTtlConfig (
    val icons: Duration = Duration.Companion.parse("PT10M"),
    val videoChunks: Duration = Duration.Companion.parse("PT1H"),
)
