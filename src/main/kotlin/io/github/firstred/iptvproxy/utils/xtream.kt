package io.github.firstred.iptvproxy.utils

import io.github.firstred.iptvproxy.enums.IptvChannelType
import java.net.URI

fun URI.toChannelType() = toString().toChannelType()
fun String.toChannelType(): IptvChannelType {
    return when {
        matches(Regex("^[^:]+://[^/]+/movie/.*"))  -> IptvChannelType.movie
        matches(Regex("^[^:]+://[^/]+/series/.*")) -> IptvChannelType.series
        else                                                -> IptvChannelType.live
    }
}
fun String.toChannelTypeOrNull(): IptvChannelType? {
    return when {
        matches(Regex("^[^:]+://[^/]+/movie/.*"))  -> IptvChannelType.movie
        matches(Regex("^[^:]+://[^/]+/series/.*")) -> IptvChannelType.series
        matches(Regex("^[^:]+://[^/]+/live/.*"))   -> IptvChannelType.series
        else                                                -> null
    }
}
