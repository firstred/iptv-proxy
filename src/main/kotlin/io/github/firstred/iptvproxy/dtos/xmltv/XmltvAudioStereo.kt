package io.github.firstred.iptvproxy.dtos.xmltv

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName

@Serializable
@SerialName("stereo")
data class XmltvAudioStereo(
    @XmlElement(false)
    @XmlSerialName("value") val value: String,
)
