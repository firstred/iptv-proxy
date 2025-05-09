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
    val proxyForwardedUser: String?
    val proxyForwardedPassword: String?

    val timeouts: IptvConnectionTimeoutsConfig

    val groupFilters: List<@Serializable(RegexPatternSerializer::class) Pattern>

    val userAgent: String?

    val epgRemapping: Map<String, String>

    fun getEpgUrl(): URI?
    fun getPlaylistUrl(): URI?
}
