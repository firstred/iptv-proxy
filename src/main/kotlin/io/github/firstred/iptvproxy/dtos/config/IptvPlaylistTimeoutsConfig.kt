package io.github.firstred.iptvproxy.dtos.config

import io.github.firstred.iptvproxy.utils.defaultMaxRetries
import kotlinx.serialization.Serializable

@Serializable
data class IptvPlaylistTimeoutsConfig(
    val playlist: IptvConnectionTimeoutsConfig = IptvConnectionTimeoutsConfig(
        connectMilliseconds = 5_000UL,
        socketMilliseconds = 60_000UL,
        totalMilliseconds = 300_000UL,
        retryDelayMilliseconds = 1_000UL,
        maxRetries = defaultMaxRetries,
    ),
    val catchup: IptvConnectionTimeoutsConfig = IptvConnectionTimeoutsConfig(
        connectMilliseconds = 3_000UL,
        socketMilliseconds = 10_000UL,
        totalMilliseconds = 30_000UL,
        retryDelayMilliseconds = 1_000UL,
        maxRetries = defaultMaxRetries,
    ),
    val vod: IptvConnectionTimeoutsConfig = IptvConnectionTimeoutsConfig(
        connectMilliseconds = 3_000UL,
        socketMilliseconds = 10_000UL,
        totalMilliseconds = 30_000UL,
        retryDelayMilliseconds = 1_000UL,
        maxRetries = defaultMaxRetries,
    ),
    val stream: IptvConnectionTimeoutsConfig = IptvConnectionTimeoutsConfig(
        connectMilliseconds = 2_000UL,
        socketMilliseconds = 5_000UL,
        totalMilliseconds = 10_000UL,
        retryDelayMilliseconds = 1_000UL,
        maxRetries = defaultMaxRetries,
    ),
)
