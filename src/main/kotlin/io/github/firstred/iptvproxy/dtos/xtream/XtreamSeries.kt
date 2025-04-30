package io.github.firstred.iptvproxy.dtos.xtream

import io.github.firstred.iptvproxy.enums.IptvChannelType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class XtreamSeries(
    val num: Int,
    val name: String,
    @SerialName("series_id") val seriesId: Int,
    @SerialName("stream_type") val streamType: IptvChannelType = IptvChannelType.series,
    val cover: String,
    val plot: String,
    val cast: String,
    val director: String,
    val genre: String,
    @SerialName("releaseDate") val releaseDate: String,
    @SerialName("last_modified") val lastModified: String,
    val rating: String,
    @SerialName("rating_5based") val rating5Based: String,
    @SerialName("backdrop_path") val backdropPath: List<String?>? = null,
    @SerialName("youtube_trailer") val youtubeTrailer: String,
    val tmdb: String,
    @SerialName("episode_run_time") val episodeRunTime: String,
    @SerialName("category_id") val categoryId: String?,
    @SerialName("category_ids") val categoryIds: List<String?>? = null,
)
