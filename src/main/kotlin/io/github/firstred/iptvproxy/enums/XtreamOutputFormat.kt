package io.github.firstred.iptvproxy.enums

import io.github.firstred.iptvproxy.serialization.serializers.enums.XtreamOutputFormatEnumSerializer
import kotlinx.serialization.Serializable

@Serializable(with = XtreamOutputFormatEnumSerializer::class)
enum class XtreamOutputFormat(val type: String) {
    M3U8("m3u8"),
    HLS("hls"),
    TS("ts"),
    RTMP("rtmp");
}
