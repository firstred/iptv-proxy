package io.github.firstred.iptvproxy.dtos.xtream

import io.github.firstred.iptvproxy.BuildConfig
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.net.URI
import java.net.URISyntaxException

@Serializable
data class XtreamServerInfo(
    val url: String,
    val port: String,
    @SerialName("https_port") val httpsPort: String? = null,
    @SerialName("server_protocol") val protocol: String,
    @SerialName("rtmp_port") val rtmpPort: String? = null,
    @SerialName("timezone") val timezone: String? = null,
    @SerialName("timestamp_now") val timestampNow: String? = null,
    @SerialName("time_now") val timeNow: String? = null,
    val process: Boolean? = null,
    val xui: Boolean = false,
    val version: String? = BuildConfig.APP_VERSION,
    val revision: String? = BuildConfig.APP_VERSION,
) {
    fun getPlaylistUrl(username: String, password: String): URI {
        try {
            val uri = URI(url)
            return URI("${uri.scheme}://${uri.host}:$port/get.php?username=$username&password=$password&type=m3u_plus&output=m3u8")
        } catch(_: URISyntaxException) {
            return URI("$protocol://$url:$port/get.php?username=$username&password=$password&type=m3u_plus&output=m3u8")
        }
    }

    fun getEpgUrl(username: String, password: String): URI {
        try {
            val uri = URI(url)
            return URI("${uri.scheme}://${uri.host}:$port/xmltv.php?username=$username&password=$password")
        } catch(_: URISyntaxException) {
            return URI("$protocol://$url:$port/xmltv.php?username=$username&password=$password")
        }
    }
}
