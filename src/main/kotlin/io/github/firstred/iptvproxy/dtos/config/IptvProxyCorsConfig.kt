package io.github.firstred.iptvproxy.dtos.config

import kotlinx.serialization.Serializable

@Serializable
data class IptvProxyCorsConfig(
    val enabled: Boolean = false,
    val allowOrigins: List<String> = listOf("*"),
    val allowCredentials: Boolean = true,
    val allowHeaders: List<String> = listOf("Content-Type", "Authorization", "Accept", "X-Requested-With", "Origin", "User-Agent", "Referer", "Accept-Encoding", "Accept-Language", "DNT", "Cache-Control"),
    val allowHeaderPrefixes: List<String> = listOf(),
    val allowMethods: List<String> = listOf("GET", "POST", "PUT", "DELETE", "OPTIONS", "HEAD"),
    val exposeHeaders: List<String> = listOf("Content-Type", "Authorization", "Accept", "X-Requested-With", "Origin", "User-Agent", "Referer", "Accept-Encoding", "Accept-Language", "DNT", "Cache-Control"),
    val maxAgeInSeconds: Long = 3600L,
)
