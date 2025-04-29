package io.github.firstred.iptvproxy.dtos.xtream

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class EpgList(
    @SerialName("epg_listings") val epgListings: List<Epg>,
)
