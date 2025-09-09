package io.github.firstred.iptvproxy.dtos.xtream

import io.github.firstred.iptvproxy.serialization.serializers.CoercedFloatSerializer
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
    @SerialName("season_number") val seasonNumber: UInt,
    @SerialName("air_date") val airDate: String? = null,
    val cover: String? = null,
    @SerialName("cover_big") val coverBig: String? = null,
    @SerialName("cover_tmdb") val coverTmdb: String? = null,
    val overview: String? = null,
    @SerialName("vote_average") @Serializable(with = CoercedFloatSerializer::class) val voteAverage: Float = 0f,
)
