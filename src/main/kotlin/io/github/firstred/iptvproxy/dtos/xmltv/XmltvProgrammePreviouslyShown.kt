package io.github.firstred.iptvproxy.dtos.xmltv

import io.github.firstred.iptvproxy.serialization.serializers.XmltvInstantSerializer
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName

@Serializable
data class XmltvProgrammePreviouslyShown(
    @XmlElement(false)
    @Serializable(with = XmltvInstantSerializer::class)
    @XmlSerialName("start") val start: Instant? = null,

    @XmlElement(false)
    @XmlSerialName("channel") val channel: String? = null,
)
