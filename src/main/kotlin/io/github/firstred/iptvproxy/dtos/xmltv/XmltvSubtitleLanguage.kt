package io.github.firstred.iptvproxy.dtos.xmltv

import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName
import nl.adaptivity.xmlutil.serialization.XmlValue

@Serializable
data class XmltvSubtitleLanguage(
    @XmlElement(false)
    @XmlSerialName("lang") val language: String? = null,

    @XmlValue
    @XmlSerialName("value") val value: String? = null,
)

