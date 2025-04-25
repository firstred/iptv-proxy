package io.github.firstred.iptvproxy.dtos.config

import io.ktor.client.engine.*

data class ProxyConfiguration(
    val type : ProxyType,
    val host: String,
    val port: Int,
    val username: String? = null,
    val password: String? = null,
)
