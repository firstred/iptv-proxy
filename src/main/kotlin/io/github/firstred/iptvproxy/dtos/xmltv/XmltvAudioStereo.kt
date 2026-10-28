package io.github.firstred.iptvproxy.dtos.xmltv

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XmlSerialName
import nl.adaptivity.xmlutil.serialization.XmlValue

@Serializable
@SerialName("stereo")
data class XmltvAudioStereo(
    @XmlValue
    @XmlSerialName("value") val value: String = "",
)
