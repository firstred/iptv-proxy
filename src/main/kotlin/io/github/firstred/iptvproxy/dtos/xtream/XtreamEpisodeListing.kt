package io.github.firstred.iptvproxy.dtos.xtream

import io.github.firstred.iptvproxy.enums.IptvChannelType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class XtreamEpisodeListing(
    val num: UInt? = null,
    val name: String? = null,
    val title: String? = null,
    val year: String? = null,
    @SerialName("series_id") val seriesId: UInt? = null,
    @SerialName("stream_type") val streamType: IptvChannelType = IptvChannelType.series,
    @SerialName("cover") val cover: String? = null,
    val plot: String? = null,
    val cast: String? = null,
    val director: String? = null,
    val genre: String? = null,
    @SerialName("releaseDate") val releasedate: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("last_modified") val lastModified: String? = null,
    val rating: String? = null,
    @SerialName("rating_5based") val rating5Based: Float? = null,
    @SerialName("backdrop_path") val backdropPath: List<String> = emptyList(),
    @SerialName("youtube_trailer") val youtubeTrailer: String? = null,
    @SerialName("episode_run_time") val episodeRunTime: String? = null,
    @SerialName("category_id") val categoryId: String = "",
    @SerialName("category_ids") val categoryIds: List<UInt> = emptyList(),
)
