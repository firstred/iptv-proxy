package io.github.firstred.iptvproxy.dtos.xtream

import io.github.firstred.iptvproxy.enums.IptvChannelType
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@OptIn(ExperimentalSerializationApi::class)
data class XtreamLiveStream(
    val num: UInt,
    val name: String,
    @SerialName("stream_type") val streamType: IptvChannelType = IptvChannelType.live,
    @SerialName("type_name") val typeName: IptvChannelType = IptvChannelType.live,
    @SerialName("stream_id") val streamId: UInt,
    @SerialName("stream_icon") val streamIcon: String? = null,
    @SerialName("epg_channel_id") val epgChannelId: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val server: String? = null,
    val added: String = "0",
    @SerialName("is_adult") val isAdult: UInt = 0u,
    @SerialName("category_id") val categoryId: String?,
    @SerialName("category_ids") val categoryIds: List<UInt?>? = null,
    @SerialName("custom_sid") val customSid: String? = null,
    @SerialName("tv_archive") val tvArchive: UInt = 0u,
    @SerialName("direct_source") val directSource: String = "",
    @SerialName("tv_archive_duration") val tvArchiveDuration: UInt = 0u,
)
