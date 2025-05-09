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
    val channelBlacklist: List<String> = emptyList(),
    val channelWhitelist: List<String> = emptyList(),
    val categoryBlacklist: List<String> = emptyList(),
    val categoryWhitelist: List<String> = emptyList(),
    val moviesEnabled: Boolean = true,
    val seriesEnabled: Boolean = true,
) {
    fun toIptvUser(): IptvUser = IptvUser(
        username = username,
        password = password,
        maxConnections = maxConnections,
        channelBlacklist = channelBlacklist,
        channelWhitelist = channelWhitelist,
        categoryBlacklist = categoryBlacklist,
        categoryWhitelist = categoryWhitelist,
        moviesEnabled = moviesEnabled,
        seriesEnabled = seriesEnabled,
    )
}
