package io.github.firstred.iptvproxy.dtos.config

import io.github.firstred.iptvproxy.classes.IptvUser
import io.github.firstred.iptvproxy.serialization.serializers.UIntWithUnderscoreSerializer
import io.github.firstred.iptvproxy.utils.defaultMaxConnections
import kotlinx.serialization.Serializable

@Serializable
data class IptvProxyUserConfig(
    val username: String = "",
    val password: String = "",
    @Serializable(with = UIntWithUnderscoreSerializer::class) val maxConnections: UInt = defaultMaxConnections,
) {
    fun toIptvUser(): IptvUser = IptvUser(
        username = username,
        password = password,
        maxConnections = maxConnections,
    )
}
