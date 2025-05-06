package io.github.firstred.iptvproxy.dtos.config

import kotlinx.serialization.Serializable

@Serializable
data class IptvProxyConfigTimeouts(
    val playlist: IptvConnectionTimeoutsConfig = IptvConnectionTimeoutsConfig(
        connectMilliseconds = 5_000UL,
        socketMilliseconds = 60_000UL,
        totalMilliseconds = 300_000UL,
        retryDelayMilliseconds = 1_000UL,
        maxRetries = 3u,
    ),
    val icon: IptvConnectionTimeoutsConfig = IptvConnectionTimeoutsConfig(
        connectMilliseconds = 5_000UL,
        socketMilliseconds = 60_000UL,
        totalMilliseconds = 300_000UL,
        retryDelayMilliseconds = 1_000UL,
        maxRetries = 3u,
    ),
)
