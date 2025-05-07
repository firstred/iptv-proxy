package io.github.firstred.iptvproxy.dtos.config

import io.github.firstred.iptvproxy.serialization.serializers.ULongWithUnderscoreSerializer
import kotlinx.serialization.Serializable

@Serializable
data class IptvProxyCacheSizeConfig(
    @Serializable(with = ULongWithUnderscoreSerializer::class) val videoChunks: ULong = 512UL * 1024UL * 1024UL,
    @Serializable(with = ULongWithUnderscoreSerializer::class) val movieInfo: ULong = 50UL * 1024UL * 1024UL,
    @Serializable(with = ULongWithUnderscoreSerializer::class) val seriesInfo: ULong = 50UL * 1024UL * 1024UL,
)
