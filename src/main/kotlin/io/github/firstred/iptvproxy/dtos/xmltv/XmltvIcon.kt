package io.github.firstred.iptvproxy.dtos.xmltv

import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName

@Serializable
data class XmltvIcon(
    @XmlElement(false)
    @XmlSerialName("src") val src: String? = null
)
