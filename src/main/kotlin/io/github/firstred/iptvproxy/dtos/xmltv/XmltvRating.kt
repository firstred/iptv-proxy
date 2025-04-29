package io.github.firstred.iptvproxy.dtos.xmltv

import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName

@Serializable
data class XmltvRating(
    @XmlElement(false)
    @XmlSerialName("system") val system: String = "",

    @XmlSerialName("value") val value: String? = null,
)
