package io.github.firstred.iptvproxy.dtos.xtream

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class XtreamEpisodeInfo(
    val airDate: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    val plot: String? = null,
    val rating: Float = 0f,
    @SerialName("movie_image") val movieImage: String = "",
    @SerialName("cover_big") val coverBig: String = "",
    @SerialName("duration_secs") val durationSecs: UInt = 0u,
    val duration: String = "",
    @SerialName("tmdb_id") val tmdbId: UInt = 0u,
    val video: XtreamVideoInfo? = null,
    val audio: XtreamAudioInfo? = null,
    val bitrate: UInt = 0u,
    val season: UInt = 0u,
)
