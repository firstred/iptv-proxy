package io.github.firstred.iptvproxy.serialization.serializers.enums

import io.github.firstred.iptvproxy.enums.XtreamOutputFormat
import io.github.firstred.iptvproxy.serialization.serializers.EnumIgnoreUndefinedSerializer

object XtreamOutputFormatEnumSerializer : EnumIgnoreUndefinedSerializer<XtreamOutputFormat>(
    XtreamOutputFormat.entries.toTypedArray(),
    XtreamOutputFormat.m3u8,                // Default value if none match
)
