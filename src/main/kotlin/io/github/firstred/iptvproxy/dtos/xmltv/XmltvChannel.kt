package io.github.firstred.iptvproxy.dtos.xmltv

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName

@Serializable
@SerialName("channel")
data class XmltvChannel(
    @XmlElement(false)
    @XmlSerialName("id") val id: String? = null,

    @SerialName("display-name")
    @XmlSerialName("display-name") val displayNames: List<XmltvText>? = null,

    @XmlSerialName("icon") val icon: XmltvIcon? = null,

    @EncodeDefault(EncodeDefault.Mode.NEVER) val server: String? = null
)
