package io.github.firstred.iptvproxy.dtos.config

import java.net.URI
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

    val userAgent: String?

    val epgRemapping: Map<String, String>
    val liveCategoryRemapping: Map<String, String>

    fun getEpgUrl(): URI?
    fun getPlaylistUrl(): URI?
}
