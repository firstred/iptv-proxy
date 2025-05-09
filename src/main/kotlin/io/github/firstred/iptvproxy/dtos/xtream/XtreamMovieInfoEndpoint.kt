package io.github.firstred.iptvproxy.dtos.xtream

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class XtreamMovieInfoEndpoint(
    val info: XtreamMovieInfo = XtreamMovieInfo(),
    @SerialName("movie_data") val movieData: XtreamMovieData = XtreamMovieData(),
    val url: String? = null,
)
