package io.github.firstred.iptvproxy.dtos.config

import io.github.firstred.iptvproxy.serialization.serializers.UIntWithUnderscoreSerializer
import kotlinx.serialization.Serializable

const val defaultVarcharLength = 255

@Serializable
data class IptvProxyDatabaseConfig(
    val jdbcUrl: String = "jdbc:sqlite::memory:",
    val username: String? = null,
    val password: String? = null,
    val maximumPoolSize: @Serializable(with = UIntWithUnderscoreSerializer::class) UInt = 1u,
    val chunkSize: @Serializable(with = UIntWithUnderscoreSerializer::class) UInt = 1_000u,
    val dataSourceProperties: Map<String, String> = emptyMap(),
)
