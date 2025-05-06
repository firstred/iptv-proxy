package io.github.firstred.iptvproxy.dtos.config

import io.github.firstred.iptvproxy.serialization.serializers.UIntWithUnderscoreSerializer
import io.ktor.client.engine.*
import kotlinx.serialization.Serializable

data class IptvConnectionProxyConfig(
    val type : ProxyType,
    val host: String,
    @Serializable(with = UIntWithUnderscoreSerializer::class) val port: UInt,
    val username: String? = null,
    val password: String? = null,
)
