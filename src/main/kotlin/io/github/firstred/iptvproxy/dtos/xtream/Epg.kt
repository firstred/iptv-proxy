package io.github.firstred.iptvproxy.dtos.xtream

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Epg(
    @SerialName("id") val externalId: String,
    @SerialName("epg_id") val epgId: String,
    val title: String,
    val lang: String,
    val start: String,
    val end: String,
    val description: String,
    @SerialName("channel_id") val channelId: String,
    @SerialName("start_timestamp") val startTimestamp: String,
    @SerialName("stop_timestamp") val stopTimestamp: String,
    @SerialName("stream_id") val streamId: String? = null,
    @SerialName("now_playing") val nowPlaying: Int? = null,
    @SerialName("has_archive") val hasArchive: Int? = null,
)
