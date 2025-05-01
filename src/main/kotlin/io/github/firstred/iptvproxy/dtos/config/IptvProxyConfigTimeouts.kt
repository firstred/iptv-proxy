package io.github.firstred.iptvproxy.dtos.config

import kotlinx.serialization.Serializable

@Serializable
data class IptvProxyConfigTimeouts(
    val playlist: ConnectionTimeoutsConfig = ConnectionTimeoutsConfig(
        connectMilliseconds = 5_000L,
        socketMilliseconds = 60_000L,
        totalMilliseconds = 300_000L,
        retryDelayMilliseconds = 1_000L,
        maxRetries = 3,
    ),
    val icon: ConnectionTimeoutsConfig = ConnectionTimeoutsConfig(
        connectMilliseconds = 5_000L,
        socketMilliseconds = 60_000L,
        totalMilliseconds = 300_000L,
        retryDelayMilliseconds = 1_000L,
        maxRetries = 3,
    ),
)
