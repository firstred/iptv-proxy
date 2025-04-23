package io.github.firstred.iptvproxy.dtos.config

import io.github.firstred.iptvproxy.IptvUser
import io.github.firstred.iptvproxy.serialization.serializers.IntWithUnderscoreSerializer
import kotlinx.serialization.Serializable

@Serializable
data class IptvProxyUserConfig(
    val username: String = "",
    val password: String = "",
    @Serializable(with = IntWithUnderscoreSerializer::class) val maxConnections: Int = 1,
) {
    fun toIptvUser(): IptvUser = IptvUser(
        username = username,
        password = password,
        maxConnections = maxConnections,
    )
}
