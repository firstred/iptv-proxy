package io.github.firstred.iptvproxy.dtos.config

import kotlinx.serialization.Serializable

@Serializable
data class IptvProxyDatabaseConfig(
    val jdbcUrl: String = "jdbc:sqlite::memory:",
    val username: String? = null,
    val password: String? = null,
    val maximumPoolSize: Int = 6,
    val chunkSize: Int = 1_000,
    val dataSourceProperties: Map<String, String> = emptyMap(),
)
