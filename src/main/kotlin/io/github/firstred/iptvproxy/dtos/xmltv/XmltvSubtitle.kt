package io.github.firstred.iptvproxy.dtos.xmltv

import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName

@Serializable
data class XmltvSubtitle(
    @XmlElement(false)
    @XmlSerialName("type") val type: String? = null,

    @XmlSerialName("value") val value: List<XmltvSubtitleLanguage>? = null,
)

