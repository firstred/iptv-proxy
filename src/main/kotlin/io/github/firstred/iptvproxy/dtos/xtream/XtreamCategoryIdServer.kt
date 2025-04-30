package io.github.firstred.iptvproxy.dtos.xtream

import io.github.firstred.iptvproxy.enums.IptvChannelType

data class XtreamCategoryIdServer(
    val id: Long,
    val externalId: Long,
    val server: String,
    val type: IptvChannelType,
)
