package io.github.firstred.iptvproxy.dtos.xmltv

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("audio")
data class XmltvAudio(
    val stereo: List<XmltvAudioStereo> = listOf()
)
