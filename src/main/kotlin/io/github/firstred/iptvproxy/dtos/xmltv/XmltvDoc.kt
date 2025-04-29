package io.github.firstred.iptvproxy.dtos.xmltv

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName

@Serializable
@SerialName("tv")
data class XmltvDoc(
    @XmlElement(false)
    @SerialName("generator-info-name")
    @XmlSerialName("generator-info-name") var generatorInfoName: String? = "iptv-proxy",

    @XmlElement(false)
    @SerialName("generator-info-url")
    @XmlSerialName("generator-info-url") var generatorInfoUrl: String? = null,

    @XmlElement(false)
    @SerialName("source-info-url")
    @XmlSerialName("source-info-url") var sourceInfoUrl: String? = null,

    @XmlElement(false)
    @SerialName("source-info-name")
    @XmlSerialName("source-info-name") var sourceInfoName: String? = null,

    @XmlElement(false)
    @SerialName("source-info-logo")
    @XmlSerialName("source-info-logo") var sourceInfoLogo: String? = null,

    @SerialName("channel")
    @XmlSerialName("channel") var channels: MutableList<XmltvChannel>? = mutableListOf(),

    @SerialName("programme")
    @XmlSerialName("programme") val programmes: MutableList<XmltvProgramme>? = mutableListOf(),
)
