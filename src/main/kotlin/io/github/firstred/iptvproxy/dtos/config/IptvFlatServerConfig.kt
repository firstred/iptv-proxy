package io.github.firstred.iptvproxy.dtos.config

import io.github.firstred.iptvproxy.serialization.serializers.RegexPatternSerializer
import kotlinx.serialization.Serializable
import java.net.URI
import java.util.regex.Pattern
import kotlin.time.Duration

@Serializable
data class IptvFlatServerConfig(
    override val name: String,
    val account: IptvServerAccountConfig,

    override val epgUrl: String? = null,
    override val epgBefore: Duration = Duration.INFINITE,
    override val epgAfter: Duration = Duration.INFINITE,

    override val proxySendUser: Boolean = false,
    override val proxyStream: Boolean = true,
    override val proxyTransparentClientHeaders: List<String> = listOf(),

    override val timeouts: IptvPlaylistConfigTimeouts = IptvPlaylistConfigTimeouts(),

    override val playlistTimeouts: ConnectionTimeoutsConfig = ConnectionTimeoutsConfig(),
    override val catchupTimeouts: ConnectionTimeoutsConfig = ConnectionTimeoutsConfig(),
    override val vodTimeouts: ConnectionTimeoutsConfig = ConnectionTimeoutsConfig(),
    override val streamTimeouts: ConnectionTimeoutsConfig = ConnectionTimeoutsConfig(),

    override val groupFilters: List<@Serializable(RegexPatternSerializer::class) Pattern> = emptyList(),
) : IIptvServerConfigWithoutAccounts {
    fun toIptvServerConfig(idx: Int): IptvServerConfig = IptvServerConfig(
        name = name,
        epgUrl = epgUrl,
        epgBefore = epgBefore,
        epgAfter = epgAfter,
        proxySendUser = proxySendUser,
        proxyStream = proxyStream,
        accounts = listOf(if (idx == 0) account else throw IllegalStateException("Only the first account is supported in flat config")),
        timeouts = timeouts,
        playlistTimeouts = playlistTimeouts,
        catchupTimeouts = catchupTimeouts,
        vodTimeouts = vodTimeouts,
        streamTimeouts = streamTimeouts,
        groupFilters = groupFilters,
    )

    override fun getEpgUrl(): URI? {
        return epgUrl?.let { URI(epgUrl) } ?: account.getEpgUrl()
    }

    override fun getPlaylistUrl(): URI? {
        return account.getPlaylistUrl()
    }
}
