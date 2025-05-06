package io.github.firstred.iptvproxy.dtos.xtream

import io.github.firstred.iptvproxy.enums.IptvChannelType

data class XtreamCategoryIdServer(
    val id: UInt,
    val externalId: UInt,
    val server: String,
    val type: IptvChannelType,
)
