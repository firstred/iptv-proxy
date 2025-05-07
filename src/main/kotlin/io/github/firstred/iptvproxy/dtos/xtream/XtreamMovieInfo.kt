package io.github.firstred.iptvproxy.dtos.xtream

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@OptIn(ExperimentalSerializationApi::class)
data class XtreamMovieInfo(
    @SerialName("kinopoisk_url") val kinopoiskUrl: String = "",
    @SerialName("tmdb_id") val tmdb_id: Int? = null,
    val name: String = "",
    @SerialName("o_name") val o_name: String = "",
    @EncodeDefault(EncodeDefault.Mode.NEVER) val cover: String? = null,
    @SerialName("cover_big") val coverBig: String = "",
    @SerialName("movie_image") val movieImage: String = "",
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("releasedate") val releasedate: String? = null,
    @SerialName("episode_run_time") val episodeRunTime: Int? = null,
    @SerialName("youtube_trailer") val youtubeTrailer: String? = null,
    val director: String? = null,
    val actors: String? = null,
    val cast: String? = null,
    val description: String? = null,
    @SerialName("plot") val plot: String? = null,
    val age: String = "",
    @SerialName("mpaa_rating") val mpaaRating: String = "",
    @SerialName("rating_count_kinopoisk") val ratingCountKinopoisk: Int = 0,
    val country: String = "",
    val genre: String? = null,
    @SerialName("backdrop_path") val backdropPath: List<String> = emptyList(),
    @SerialName("duration_secs") val durationSecs: Int = 0,
    val duration: String = "",
    val bitrate: Int = 0,
    val subtitles: List<String> = emptyList(),
    val rating: Float = 0f,
)
