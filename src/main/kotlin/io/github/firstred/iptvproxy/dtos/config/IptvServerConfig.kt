package io.github.firstred.iptvproxy.dtos.config

import io.github.firstred.iptvproxy.entities.IptvServer
import io.github.firstred.iptvproxy.entities.IptvServerConnection
import io.github.firstred.iptvproxy.serialization.serializers.RegexPatternSerializer
import kotlinx.serialization.Serializable
import java.net.URI
import java.util.regex.Pattern
import kotlin.time.Duration

@Serializable
data class IptvServerConfig(
    override val name: String,

    override val epgUrl: String? = null,
    override val epgBefore: Duration = Duration.INFINITE,
    override val epgAfter: Duration = Duration.INFINITE,

    override val proxyStream: Boolean = true,
    override val proxyForwardedUser: String? = null,
    override val proxyForwardedPassword: String? = null,

    val accounts: List<IptvServerAccountConfig>? = null,

    override val timeouts: ConnectionTimeoutsConfig = ConnectionTimeoutsConfig(),

    override val groupFilters: List<@Serializable(RegexPatternSerializer::class) Pattern> = emptyList(),

    override val userAgent: String? = null,
) : IIptvServerConfigWithoutAccounts {
    fun toFlatIptvServerConfig(accountIndex: Int) = IptvFlatServerConfig(
        name = name,
        epgUrl = epgUrl,
        epgBefore = epgBefore,
        epgAfter = epgAfter,
        proxyForwardedUser = proxyForwardedUser,
        proxyForwardedPassword = proxyForwardedPassword,
        proxyStream = proxyStream,
        account = accounts?.let { it[accountIndex] } ?: throw IllegalStateException("No account configured for this server at index $accountIndex"),
        timeouts = timeouts,
        groupFilters = groupFilters,
        userAgent = userAgent,
    )

    fun toIptvServer(): IptvServer = IptvServer(
        name = name,
        config = this,
        connections = accounts?.mapIndexed { idx, _ -> IptvServerConnection(toFlatIptvServerConfig(idx)) }?.toMutableList() ?: mutableListOf(),
    )

    override fun getEpgUrl(): URI? {
        return epgUrl?.let { URI(epgUrl) } ?: accounts?.firstOrNull()?.getEpgUrl()
    }

    override fun getPlaylistUrl(): URI? {
        return accounts?.firstOrNull()?.getPlaylistUrl()
    }
}
