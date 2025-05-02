package io.github.firstred.iptvproxy.dtos.xtream

import io.github.firstred.iptvproxy.enums.IptvChannelType
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@OptIn(ExperimentalSerializationApi::class)
data class XtreamMovie(
    val num: Long,
    val name: String,
    @SerialName("stream_type") val streamType: IptvChannelType = IptvChannelType.movie,
    @SerialName("type_name") val typeName: IptvChannelType = IptvChannelType.movie,
    @SerialName("stream_id") val streamId: Long,
    @SerialName("stream_icon") val streamIcon: String,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val server: String? = null,
    val rating: String,
    @SerialName("rating_5based") val rating5Based: Double,
    val tmdb: String,
    val trailer: String,
    val added: String,
    @SerialName("is_adult") val isAdult: Int,
    @SerialName("category_id") val categoryId: String?,
    @SerialName("category_ids") val categoryIds: List<Long?>? = null,
    @SerialName("container_extension") val containerExtension: String,
    @SerialName("custom_sid") val customSid: String? = null,
    @SerialName("direct_source") val directSource: String,
)
