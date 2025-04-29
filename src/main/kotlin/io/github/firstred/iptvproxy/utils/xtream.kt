package io.github.firstred.iptvproxy.utils

import io.github.firstred.iptvproxy.enums.IptvChannelType
import java.net.URI

fun URI.channelType() = toString().channelType()
fun String.channelType(): IptvChannelType {
    return when {
        matches(Regex("^[^:]+://[^/]+/movie/.*"))  -> IptvChannelType.MOVIE
        matches(Regex("^[^:]+://[^/]+/series/.*")) -> IptvChannelType.SERIES
        else                                                -> IptvChannelType.LIVE
    }
}
