package io.github.firstred.iptvproxy.dtos.xtream

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class XtreamAudioInfo(
    val index: UInt = 0u,
    @SerialName("codec_name") val codecName: String = "",
    @SerialName("codec_long_name") val codecLongName: String = "",
    @SerialName("codec_type") val codecType: String = "",
    @SerialName("codec_tag_string") val codecTagString: String = "",
    @SerialName("codec_tag") val codecTag: String = "",
    @SerialName("sample_fmt") val sampleFmt: String = "",
    @SerialName("sample_rate") val sampleRate: String = "",
    val channels: UInt = 0u,
    @SerialName("channel_layout") val channelLayout: String = "",
    @SerialName("bits_per_sample") val bitsPerSample: UInt = 0u,
    @SerialName("r_frame_rate") val rFrameRate: String = "",
    @SerialName("avg_frame_rate") val avgFrameRate: String = "",
    @SerialName("time_base") val timeBase: String = "",
    @SerialName("start_pts") val startPts: Int = 0,
    @SerialName("start_time") val startTime: String = "",
    @SerialName("bit_rate") val bitRate: String = "",
    val disposition: XtreamDisposition = XtreamDisposition(),
    val tags: Map<String, String> = emptyMap(),
)
