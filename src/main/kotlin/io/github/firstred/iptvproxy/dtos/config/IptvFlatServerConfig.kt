package io.github.firstred.iptvproxy.dtos.config

import io.github.firstred.iptvproxy.utils.toEncodedJavaURI
import io.ktor.http.*
import kotlinx.serialization.Serializable
import java.net.URI
import kotlin.time.Duration

@Serializable
data class IptvFlatServerConfig(
    override val name: String,
    val account: IptvServerAccountConfig? = null,

    override val epgUrl: String? = null,
    override val epgBefore: Duration = Duration.INFINITE,
    override val epgAfter: Duration = Duration.INFINITE,

    override val proxyStream: Boolean = true,
    override val proxyForwardedUser: String? = null,
    override val proxyForwardedPassword: String? = null,

    override val timeouts: IptvConnectionTimeoutsConfig = IptvConnectionTimeoutsConfig(),

    override val userAgent: String? = null,

    override val epgRemapping: Map<String, String> = mapOf(),
    override val liveCategoryRemapping: Map<String, String> = mapOf(),
) : IIptvServerConfigWithoutAccounts {
    fun toIptvServerConfig(idx: UInt): IptvServerConfig = IptvServerConfig(
        name = name,
        epgUrl = epgUrl,
        epgBefore = epgBefore,
        epgAfter = epgAfter,
        proxyForwardedUser = proxyForwardedUser,
        proxyForwardedPassword = proxyForwardedPassword,
        proxyStream = proxyStream,
        accounts = account?.let { listOf(if (idx == 0u) it else throw IllegalStateException("Only the first account is supported in flat config")) } ?: emptyList(),
        timeouts = timeouts,
        userAgent = userAgent,
        epgRemapping = epgRemapping,
        liveCategoryRemapping = liveCategoryRemapping,
    )

    override fun getEpgUrl(): URI? {
        return epgUrl?.let { Url(epgUrl).toEncodedJavaURI() } ?: account?.getEpgUrl()
    }

    override fun getPlaylistUrl(): URI? {
        return account?.getPlaylistUrl()
    }

    fun remapEpgChannelId(epgChannelId: String): String {
        for ((key, value) in epgRemapping) {
            if (key.startsWith("regexp:")) {
                val pattern = key.substringAfter("regexp:").toRegex()
                if (pattern.matches(epgChannelId)) return epgChannelId.replace(pattern, value)
            } else if (key == epgChannelId) {
                return value
            }
        }

        return epgChannelId
    }
}
