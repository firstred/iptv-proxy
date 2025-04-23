package io.github.firstred.iptvproxy.dtos.config

import io.github.firstred.iptvproxy.serialization.serializers.IntWithUnderscoreSerializer
import io.github.firstred.iptvproxy.serialization.serializers.LongWithUnderscoreSerializer
import io.github.firstred.iptvproxy.utils.defaultMaxRetries
import kotlinx.serialization.Serializable

@Serializable
data class ConnectionTimeoutsConfig(
    @Serializable(with = LongWithUnderscoreSerializer::class) val connectMilliseconds: Long = 5_000L,
    @Serializable(with = LongWithUnderscoreSerializer::class) val socketMilliseconds: Long = 60_000L,
    @Serializable(with = LongWithUnderscoreSerializer::class) val totalMilliseconds: Long = 300_000L,
    @Serializable(with = LongWithUnderscoreSerializer::class) val retryDelayMilliseconds: Long = 1_000L,
    @Serializable(with = IntWithUnderscoreSerializer::class) val maxRetries: Int = defaultMaxRetries,
)
