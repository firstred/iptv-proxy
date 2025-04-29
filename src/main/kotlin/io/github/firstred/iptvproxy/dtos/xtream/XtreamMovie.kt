package io.github.firstred.iptvproxy.dtos.xtream

import io.github.firstred.iptvproxy.enums.IptvChannelType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class XtreamMovie(
    val num: Int,
    val name: String,
    @SerialName("stream_type") val streamType: IptvChannelType = IptvChannelType.MOVIE,
    @SerialName("stream_id") val streamId: Int,
    @SerialName("stream_icon") val streamIcon: String,
    val rating: String,
    @SerialName("rating_5based") val rating5Based: Double,
    val tmdb: String,
    val trailer: String,
    val added: String,
    @SerialName("is_adult") val isAdult: Int,
    @SerialName("category_id") val categoryId: String,
    @SerialName("category_ids") val categoryIds: List<String>,
    @SerialName("container_extension") val containerExtension: String,
    @SerialName("custom_sid") val customSid: String? = null,
    @SerialName("direct_source") val directSource: String,
)
