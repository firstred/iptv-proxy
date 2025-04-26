package io.github.firstred.iptvproxy.dtos.xtream

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class XtreamSeriesCategory(
    @SerialName("category_id") val id: String,
    @SerialName("category_name") val name: String,
    @SerialName("parent_id") val parentId: String = "0",
)
