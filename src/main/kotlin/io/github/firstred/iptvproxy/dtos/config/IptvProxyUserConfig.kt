package io.github.firstred.iptvproxy.dtos.config

import io.github.firstred.iptvproxy.entities.IptvUser
import io.github.firstred.iptvproxy.serialization.serializers.IntWithUnderscoreSerializer
import io.github.firstred.iptvproxy.utils.defaultMaxConnections
import kotlinx.serialization.Serializable

@Serializable
data class IptvProxyUserConfig(
    val username: String = "",
    val password: String = "",
    @Serializable(with = IntWithUnderscoreSerializer::class) val maxConnections: Int = defaultMaxConnections,
) {
    fun toIptvUser(): IptvUser = IptvUser(
        username = username,
        password = password,
        maxConnections = maxConnections,
    )
}
