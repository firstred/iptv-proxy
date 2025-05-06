package io.github.firstred.iptvproxy.dtos.xtream

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class XtreamEpgList(
    @SerialName("epg_listings") val epgListings: List<XtreamEpg>,
)
