package io.github.firstred.iptvproxy.dtos.config

import io.github.firstred.iptvproxy.serialization.serializers.RegexPatternSerializer
import kotlinx.serialization.Serializable
import java.net.URI
import java.util.regex.Pattern
import kotlin.time.Duration

interface IIptvServerConfigWithoutAccounts {
    val name: String

    val epgUrl: String?
    val epgBefore: Duration
    val epgAfter: Duration

    val proxyStream: Boolean
    val proxySendUser: Boolean

    val timeouts: IptvPlaylistConfigTimeouts

    val playlistTimeouts: ConnectionTimeoutsConfig
    val catchupTimeouts: ConnectionTimeoutsConfig
    val vodTimeouts: ConnectionTimeoutsConfig
    val streamTimeouts: ConnectionTimeoutsConfig

    val groupFilters: List<@Serializable(RegexPatternSerializer::class) Pattern>

    fun getEpgUrl(): URI?
    fun getPlaylistUrl(): URI?
}
