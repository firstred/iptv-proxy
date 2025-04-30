package io.github.firstred.iptvproxy.serialization.serializers.enums

import io.github.firstred.iptvproxy.enums.IptvChannelType
import io.github.firstred.iptvproxy.serialization.serializers.EnumIgnoreUndefinedSerializer

object IptvChannelTypeEnumSerializer : EnumIgnoreUndefinedSerializer<IptvChannelType>(
    IptvChannelType.entries.toTypedArray(),
    IptvChannelType.live
)
