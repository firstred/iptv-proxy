package io.github.firstred.iptvproxy.dtos.config

import io.github.firstred.iptvproxy.serialization.serializers.UIntWithUnderscoreSerializer
import io.github.firstred.iptvproxy.serialization.serializers.ULongWithUnderscoreSerializer
import io.github.firstred.iptvproxy.utils.defaultMaxRetries
import kotlinx.serialization.Serializable

@Serializable
data class IptvConnectionTimeoutsConfig(
    @Serializable(with = ULongWithUnderscoreSerializer::class) val connectMilliseconds: ULong = 5_000UL,
    @Serializable(with = ULongWithUnderscoreSerializer::class) val socketMilliseconds: ULong = 20_000UL,
    @Serializable(with = ULongWithUnderscoreSerializer::class) val totalMilliseconds: ULong = 60_000UL,
    @Serializable(with = ULongWithUnderscoreSerializer::class) val retryDelayMilliseconds: ULong = 2_000UL,
    @Serializable(with = UIntWithUnderscoreSerializer::class) val maxRetries: UInt = defaultMaxRetries,
)
