package io.github.firstred.iptvproxy.dtos.xtream

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class XtreamEpisode(
    val id: String,
    @SerialName("episode_num") val episodeNum: String? = null,
    val title: String? = null,
    @SerialName("container_extension") val containerExtension: String ? = null,
    val info: XtreamEpisodeInfo,
    @SerialName("custom_sid") val customSid: String? = null,
    val added: String? = null,
    val season: UInt? = null,
    @SerialName("direct_source") val directSource: String? = null,
    val subtitles: List<String> = emptyList(),
    val url: String? = null,
)
