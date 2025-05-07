package io.github.firstred.iptvproxy.dtos.xtream

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class XtreamVideoInfo(
    val index: Int = 0,
    @SerialName("codec_info") val codecName: String = "",
    @SerialName("codec_long_name") val codecLongName: String = "",
    val profile: String = "",
    @SerialName("codec_type") val codecType: String = "",
    @SerialName("codec_tag_string") val codecTagString: String = "",
    @SerialName("codec_tag") val codecTag: String = "",
    val width: Int = 0,
    val height: Int = 0,
    val codedWidth: Int = 0,
    val codedHeight: Int = 0,
    @SerialName("closed_captions") val closedCaptions: Int = 0,
    @SerialName("film_grain") val filmGrain: Int = 0,
    @SerialName("has_b_frames") val hasBFrames: Int = 0,
    @SerialName("sample_aspect_ratio") val sampleAspectRatio: String = "",
    @SerialName("display_aspect_ratio") val displayAspectRatio: String = "",
    @SerialName("pix_fmt") val pixFmt: String = "",
    val level: Int = 0,
    @SerialName("color_range") val colorRange: String = "",
    @SerialName("color_space") val colorSpace: String = "",
    @SerialName("color_transfer") val colorTransfer: String = "",
    @SerialName("color_primaries") val colorPrimaries: String = "",
    @SerialName("chroma_location") val chromaLocation: String = "",
    @SerialName("field_order") val fieldOrder: String = "",
    val refs: Int = 0,
    @SerialName("is_avc") val isAvc: String = "",
    @SerialName("nal_length_size") val nalLengthSize: String = "",
    @SerialName("r_frame_rate") val rFrameRate: String = "",
    @SerialName("avg_frame_rate") val avgFrameRate: String = "",
    @SerialName("time_base") val timeBase: String = "",
    @SerialName("start_pts") val startPts: UInt = 0u,
    @SerialName("start_time") val startTime: String = "",
    @SerialName("bits_per_raw_sample") val bitsPerRawSample: String = "",
    @SerialName("extradata_size") val extradataSize: Int = 0,
    val disposition: XtreamDisposition = XtreamDisposition(),
    val tags: Map<String, String> = emptyMap(),
)
