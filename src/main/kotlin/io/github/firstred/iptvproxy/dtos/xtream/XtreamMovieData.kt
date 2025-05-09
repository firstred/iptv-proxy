package io.github.firstred.iptvproxy.dtos.xtream

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class XtreamMovieData(
    @SerialName("stream_id") val streamId: Int = 0,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val name: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val title: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val cover: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val year: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val added: String? = null,
    @SerialName("category_id") val categoryId: String = "",
    @SerialName("category_ids") val categoryIds: List<Int> = emptyList(),
    @SerialName("container_extension") val containerExtension: String = "",
    @SerialName("custom_sid") val customSid: String = "",
    @SerialName("direct_source") val directSource: String = "",
)
