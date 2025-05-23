package io.github.firstred.iptvproxy.dtos.config

import kotlinx.serialization.Serializable

@Serializable
data class IptvProxySentryConfig(
    val dsn: String? = null,
    val environment: String? = null,
    val release: String? = null,
    val debug: Boolean? = null,
)
