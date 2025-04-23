package io.github.firstred.iptvproxy.dtos.config

import io.github.firstred.iptvproxy.utils.defaultMaxRetries
import kotlinx.serialization.Serializable

@Serializable
data class IptvPlaylistConfigTimeouts(
    val playlist: ConnectionTimeoutsConfig = ConnectionTimeoutsConfig(
        connectMilliseconds = 5_000L,
        socketMilliseconds = 60_000L,
        totalMilliseconds = 300_000L,
        retryDelayMilliseconds = 1_000L,
        maxRetries = defaultMaxRetries,
    ),
    val catchup: ConnectionTimeoutsConfig = ConnectionTimeoutsConfig(
        connectMilliseconds = 3_000L,
        socketMilliseconds = 10_000L,
        totalMilliseconds = 30_000L,
        retryDelayMilliseconds = 1_000L,
        maxRetries = defaultMaxRetries,
    ),
    val vod: ConnectionTimeoutsConfig = ConnectionTimeoutsConfig(
        connectMilliseconds = 3_000L,
        socketMilliseconds = 10_000L,
        totalMilliseconds = 30_000L,
        retryDelayMilliseconds = 1_000L,
        maxRetries = defaultMaxRetries,
    ),
    val stream: ConnectionTimeoutsConfig = ConnectionTimeoutsConfig(
        connectMilliseconds = 2_000L,
        socketMilliseconds = 5_000L,
        totalMilliseconds = 10_000L,
        retryDelayMilliseconds = 1_000L,
        maxRetries = defaultMaxRetries,
    ),
)
