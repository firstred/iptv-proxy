package io.github.firstred.iptvproxy.dtos.xtream

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.net.URI

@Serializable
data class XtreamProfile(
    @SerialName("user_info") val user: XtreamUserInfo,
    @SerialName("server_info") val server: XtreamServerInfo,
) {
    fun getPlaylistUrl(): URI = server.getPlaylistUrl(user.username, user.password)
    fun getEpgUrl(): URI = server.getEpgUrl(user.username, user.password)
}
