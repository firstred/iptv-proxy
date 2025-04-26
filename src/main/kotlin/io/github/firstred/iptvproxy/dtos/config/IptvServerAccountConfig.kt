package io.github.firstred.iptvproxy.dtos.config

import io.github.firstred.iptvproxy.serialization.serializers.IntWithUnderscoreSerializer
import io.github.firstred.iptvproxy.utils.defaultMaxConnections
import kotlinx.serialization.Serializable
import java.net.URI
import java.net.URISyntaxException

@Serializable
data class IptvServerAccountConfig(
    var idx: Int = -1,
    val url: String? = null,
    val login: String? = null,
    val password: String? = null,
    val xtreamUsername: String? = null,
    val xtreamPassword: String? = null,
    @Serializable(with = IntWithUnderscoreSerializer::class) val maxConcurrentRequests: Int = defaultMaxConnections,
    @Serializable(with = IntWithUnderscoreSerializer::class) val maxConcurrentRequestsPerHost: Int = defaultMaxConnections,
    @Serializable(with = IntWithUnderscoreSerializer::class) val maxConcurrentRequestsPerChannel: Int = Int.MAX_VALUE, // Unused -- reserved for future use
    val userAgent: String? = null,
) : Comparable<IptvServerAccountConfig> {
    fun getPlaylistUrl(): URI? {
        if (!xtreamUsername.isNullOrBlank() && !xtreamPassword.isNullOrBlank() && !url.isNullOrBlank()) {
            try {
                val uri = URI(url)
                return URI("${uri.scheme}://${uri.host}:${uri.port}/get.php?username=$xtreamUsername&password=$xtreamPassword&type=m3u_plus&output=m3u8")
            } catch (_: URISyntaxException) {
            }
        }

        return try {
            url?.let { URI(it) }
        } catch (_: URISyntaxException) {
            null
        }
    }
    fun getEpgUrl(): URI? {
        if (!xtreamUsername.isNullOrBlank() && !xtreamPassword.isNullOrBlank() && !url.isNullOrBlank()) {
            try {
                val uri = URI(url)
                return URI("${uri.scheme}://${uri.host}:${uri.port}/xmltv.php?username=$xtreamUsername&password=$xtreamPassword")
            } catch (_: URISyntaxException) {
            }
        }

        return null
    }

    override fun compareTo(other: IptvServerAccountConfig): Int {
        return when {
            url != other.url -> url?.compareTo(other.url ?: "") ?: -1
            login != other.login -> login?.compareTo(other.login ?: "") ?: -1
            password != other.password -> password?.compareTo(other.password ?: "") ?: -1
            xtreamUsername != other.xtreamUsername -> xtreamUsername?.compareTo(other.xtreamUsername ?: "") ?: -1
            xtreamPassword != other.xtreamPassword -> xtreamPassword?.compareTo(other.xtreamPassword ?: "") ?: -1
            maxConcurrentRequests != other.maxConcurrentRequests -> maxConcurrentRequests.compareTo(other.maxConcurrentRequests)
            maxConcurrentRequestsPerHost != other.maxConcurrentRequestsPerHost -> maxConcurrentRequestsPerHost.compareTo(
                other.maxConcurrentRequestsPerHost
            )

            maxConcurrentRequestsPerChannel != other.maxConcurrentRequestsPerChannel -> maxConcurrentRequestsPerChannel.compareTo(
                other.maxConcurrentRequestsPerChannel
            )

            userAgent != other.userAgent -> userAgent?.compareTo(other.userAgent ?: "") ?: -1
            else -> 0
        }
    }
}
