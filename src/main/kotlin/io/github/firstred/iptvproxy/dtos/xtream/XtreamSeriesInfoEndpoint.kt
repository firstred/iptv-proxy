package io.github.firstred.iptvproxy.dtos.xtream

import kotlinx.serialization.Serializable

@Serializable
data class XtreamSeriesInfoEndpoint(
    val seasons: List<XtreamSeason>,
    val info: XtreamEpisodeListing,
    val episodes: Map<String, List<XtreamEpisode>>,
)
