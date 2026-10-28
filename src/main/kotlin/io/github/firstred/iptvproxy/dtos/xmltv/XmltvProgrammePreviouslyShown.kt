package io.github.firstred.iptvproxy.dtos.xmltv

import io.github.firstred.iptvproxy.serialization.serializers.XmltvInstantSerializer
import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName
import kotlin.time.Instant

@Serializable
data class XmltvProgrammePreviouslyShown(
    @XmlElement(false)
    @Serializable(with = XmltvInstantSerializer::class)
    @XmlSerialName("start") val start: Instant? = null,

    @XmlElement(false)
    @XmlSerialName("channel") val channel: String? = null,
)
