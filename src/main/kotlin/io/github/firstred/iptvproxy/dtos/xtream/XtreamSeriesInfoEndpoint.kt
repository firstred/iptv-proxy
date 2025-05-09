package io.github.firstred.iptvproxy.dtos.xtream

import kotlinx.serialization.Serializable

@Serializable
data class XtreamSeriesInfoEndpoint(
    val seasons: List<XtreamSeason> = listOf(),
    val info: XtreamEpisodeListing = XtreamEpisodeListing(),
    val episodes: Map<String, List<XtreamEpisode>> = mapOf(),
)
