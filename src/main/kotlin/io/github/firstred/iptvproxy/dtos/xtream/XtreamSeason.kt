package io.github.firstred.iptvproxy.dtos.xtream

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@OptIn(ExperimentalSerializationApi::class)
data class XtreamSeason(
    @EncodeDefault(EncodeDefault.Mode.NEVER) val id: UInt? = null,
    val name: String,
    @SerialName("episode_count") val episodeCount: UInt,
    val overview: String,
    @SerialName("air_date") val airDate: String? = null,
    val cover: String = "",
    @SerialName("cover_tmdb") val coverTmdb: String = "",
    @SerialName("season_number") val seasonNumber: UInt,
    @SerialName("cover_big") val coverBig: String = "",
    @SerialName("vote_average") val voteAverage: Float = 0f,
)
