package io.github.firstred.iptvproxy.dtos.xtream

import io.github.firstred.iptvproxy.enums.IptvChannelType
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class XtreamLiveStream(
    val num: Int,
    val name: String,
    @SerialName("stream_type") val streamType: IptvChannelType = IptvChannelType.LIVE,
    @SerialName("stream_id") val streamId: Int,
    @SerialName("stream_icon") val streamIcon: String,
    @SerialName("epg_channel_id") val epgChannelId: String,
    val added: Instant,
    @SerialName("is_adult") val isAdult: Int,
    @SerialName("category_id") val categoryId: String,
    @SerialName("category_ids") val categoryIds: List<String>,
    @SerialName("custom_sid") val customSid: String? = null,
    @SerialName("tv_archive") val tvArchive: Int,
    @SerialName("direct_source") val directSource: String,
    @SerialName("tv_archive_duration") val tvArchiveDuration: Int,
)
