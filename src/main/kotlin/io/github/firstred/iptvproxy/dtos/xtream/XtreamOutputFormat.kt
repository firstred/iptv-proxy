package io.github.firstred.iptvproxy.dtos.xtream

import io.github.firstred.iptvproxy.serialization.serializers.XtreamOutputFormatEnumSerializer
import kotlinx.serialization.Serializable

@Serializable(with = XtreamOutputFormatEnumSerializer::class)
enum class XtreamOutputFormat(val type: String) {
    M3U8("m3u8"),
    TS("ts"),
    RTMP("rtmp");

    companion object {
        fun fromString(value: String): XtreamOutputFormat? {
            return try {
                valueOf(value.uppercase())
            } catch (e: IllegalArgumentException) {
                null
            }
        }
    }
}
