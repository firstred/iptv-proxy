package io.github.firstred.iptvproxy.dtos.config

import io.github.firstred.iptvproxy.enums.XtreamOutputFormat
import io.github.firstred.iptvproxy.serialization.serializers.UIntWithUnderscoreSerializer
import io.github.firstred.iptvproxy.utils.defaultMaxConnections
import io.github.firstred.iptvproxy.utils.toEncodedJavaURI
import io.ktor.http.*
import kotlinx.serialization.Serializable
import java.net.URI
import java.net.URISyntaxException

@Serializable
data class IptvServerAccountConfig(
    val url: String? = null,
    val login: String? = null,
    val password: String? = null,
    val xtreamUsername: String? = null,
    val xtreamPassword: String? = null,
    val xtreamOutput: XtreamOutputFormat? = null,
    @Serializable(with = UIntWithUnderscoreSerializer::class) val maxConcurrentRequests: UInt = defaultMaxConnections,
    @Serializable(with = UIntWithUnderscoreSerializer::class) val maxConcurrentRequestsPerHost: UInt = defaultMaxConnections,
    @Serializable(with = UIntWithUnderscoreSerializer::class) val maxConcurrentRequestsPerChannel: UInt = UInt.MAX_VALUE, // Unused -- reserved for future use
) : Comparable<IptvServerAccountConfig> {
    fun getPlaylistUrl(): URI? {
        if (isXtream()) {
            try {
                val uri = Url("$url").toEncodedJavaURI()
                return URI("${uri.scheme}://${uri.host}:${if (uri.port > 0) uri.port else (if ("https" == uri.scheme) 443 else 80)}/get.php?username=$xtreamUsername&password=$xtreamPassword&type=m3u_plus&output=${xtreamOutput ?: XtreamOutputFormat.m3u8}")
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
        if (isXtream()) {
            try {
                val uri = Url("$url").toEncodedJavaURI()
                return URI("${uri.scheme}://${uri.host}:${if (uri.port > 0) uri.port else (if ("https" == uri.scheme) 443 else 80)}/xmltv.php?username=$xtreamUsername&password=$xtreamPassword")
            } catch (_: URISyntaxException) {
            }
        }

        return null
    }
    fun getXtreamUserInfoUrl(): URI? {
        if (!isXtream()) return null

        try {
            val uri = Url("$url").toEncodedJavaURI()
            return URI("${uri.scheme}://${uri.host}:${if (uri.port > 0) uri.port else (if ("https" == uri.scheme) 443 else 80)}/player_api.php?username=$xtreamUsername&password=$xtreamPassword")
        } catch (_: URISyntaxException) {
        }

        return null
    }
    fun getXtreamLiveStreamCategoriesUrl(): URI? {
        if (!isXtream() || null == url) return null

        try {
            val uri = Url("$url").toEncodedJavaURI()
            return URI("${uri.scheme}://${uri.host}:${if (uri.port > 0) uri.port else (if ("https" == uri.scheme) 443 else 80)}/player_api.php?username=$xtreamUsername&password=$xtreamPassword&action=get_live_categories")
        } catch (_: URISyntaxException) {
        }
        return null
    }
    fun getXtreamLiveStreamsUrl(): URI? {
        if (!isXtream() || null == url) return null

        try {
            val uri = Url(url).toEncodedJavaURI()
            return URI("${uri.scheme}://${uri.host}:${if (uri.port > 0) uri.port else (if ("https" == uri.scheme) 443 else 80)}/player_api.php?username=$xtreamUsername&password=$xtreamPassword&action=get_live_streams")
        } catch (_: URISyntaxException) {
        }
        return null
    }
    fun getXtreamMovieCategoriesUrl(): URI? {
        if (!isXtream() || null == url) return null

        try {
            val uri = Url(url).toEncodedJavaURI()
            return URI("${uri.scheme}://${uri.host}:${if (uri.port > 0) uri.port else (if ("https" == uri.scheme) 443 else 80)}/player_api.php?username=$xtreamUsername&password=$xtreamPassword&action=get_vod_categories")
        } catch (_: URISyntaxException) {
        }
        return null
    }
    fun getXtreamMoviesUrl(): URI? {
        if (!isXtream() || null == url) return null

        try {
            val uri = Url(url).toEncodedJavaURI()
            return URI("${uri.scheme}://${uri.host}:${if (uri.port > 0) uri.port else (if ("https" == uri.scheme) 443 else 80)}/player_api.php?username=$xtreamUsername&password=$xtreamPassword&action=get_vod_streams")
        } catch (_: URISyntaxException) {
        }
        return null
    }
    fun getXtreamMovieInfoUrl(): URI? {
        if (!isXtream() || null == url) return null

        try {
            val uri = Url(url).toEncodedJavaURI()
            return URI("${uri.scheme}://${uri.host}:${if (uri.port > 0) uri.port else (if ("https" == uri.scheme) 443 else 80)}/player_api.php?username=$xtreamUsername&password=$xtreamPassword&action=get_vod_info")
        } catch (_: URISyntaxException) {
        }
        return null
    }
    fun getXtreamSeriesCategoriesUrl(): URI? {
        if (!isXtream() || null == url) return null

        try {
            val uri = Url(url).toEncodedJavaURI()
            return URI("${uri.scheme}://${uri.host}:${if (uri.port > 0) uri.port else (if ("https" == uri.scheme) 443 else 80)}/player_api.php?username=$xtreamUsername&password=$xtreamPassword&action=get_series_categories")
        } catch (_: URISyntaxException) {
        }
        return null
    }
    fun getXtreamSeriesUrl(): URI? {
        if (!isXtream() || null == url) return null

        try {
            val uri = Url(url).toEncodedJavaURI()
            return URI("${uri.scheme}://${uri.host}:${if (uri.port > 0) uri.port else (if ("https" == uri.scheme) 443 else 80)}/player_api.php?username=$xtreamUsername&password=$xtreamPassword&action=get_series")
        } catch (_: URISyntaxException) {
        }
        return null
    }
    fun getXtreamSeriesInfoUrl(): URI? {
        if (!isXtream() || null == url) return null

        try {
            val uri = Url(url).toEncodedJavaURI()
            return URI("${uri.scheme}://${uri.host}:${if (uri.port > 0) uri.port else (if ("https" == uri.scheme) 443 else 80)}/player_api.php?username=$xtreamUsername&password=$xtreamPassword&action=get_series_info")
        } catch (_: URISyntaxException) {
        }
        return null
    }

    fun isXtream(): Boolean =
        !xtreamUsername.isNullOrBlank() && !xtreamPassword.isNullOrBlank() && !url.isNullOrBlank()

    override fun compareTo(other: IptvServerAccountConfig): Int {
        // Compares account configurations based on properties
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

            else -> 0
        }
    }
}
