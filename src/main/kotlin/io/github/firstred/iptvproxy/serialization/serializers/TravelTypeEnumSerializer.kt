package io.github.firstred.iptvproxy.serialization.serializers

import io.github.firstred.iptvproxy.dtos.xtream.XtreamOutputFormat

object XtreamOutputFormatEnumSerializer : EnumIgnoreUndefinedSerializer<XtreamOutputFormat>(
    XtreamOutputFormat.entries.toTypedArray(),
    XtreamOutputFormat.M3U8,                // Default value if none match
)
