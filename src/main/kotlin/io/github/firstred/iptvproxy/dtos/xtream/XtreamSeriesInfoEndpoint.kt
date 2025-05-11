package io.github.firstred.iptvproxy.dtos.xtream

import io.github.firstred.iptvproxy.serialization.serializers.EnsureMappedEpisodesJsonMapSerializer
import kotlinx.serialization.Serializable

@Serializable
data class XtreamSeriesInfoEndpoint(
    val seasons: List<XtreamSeason> = listOf(),
    val info: XtreamEpisodeListing = XtreamEpisodeListing(),
    @Serializable(with = EnsureMappedEpisodesJsonMapSerializer::class) val episodes: Map<String, List<XtreamEpisode>> = mapOf(),
)
