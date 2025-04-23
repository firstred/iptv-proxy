package io.github.firstred.iptvproxy.dtos.xmltv

import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName
import nl.adaptivity.xmlutil.serialization.XmlValue

@Serializable
data class XmltvEpisodeNum(
    @XmlElement(false)
    @XmlSerialName("system") val system: String? = null,

    @XmlValue
    @XmlSerialName("value") val value: String? = null,
)
