package io.github.firstred.iptvproxy.dtos.xmltv

import io.github.firstred.iptvproxy.serialization.serializers.XmltvInstantSerializer
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName

@Serializable
data class XmltvProgramme(
    @XmlElement(false)
    @Serializable(with = XmltvInstantSerializer::class)
    @XmlSerialName("start") val start: Instant? = null,

    @XmlElement(false)
    @Serializable(with = XmltvInstantSerializer::class)
    @XmlSerialName("stop") val stop: Instant? = null,

    @XmlElement(false)
    @XmlSerialName("channel") val channel: String? = null,

    @XmlSerialName("category") val category: XmltvText? = null,

    @XmlSerialName("title") val title: XmltvText? = null,

    @XmlSerialName("sub-title") val subTitle: XmltvText? = null,

    @XmlSerialName("desc") val desc: XmltvText? = null,

    @XmlSerialName("rating") val rating: XmltvRating? = null,

    @XmlSerialName("icon") val icon: XmltvIcon? = null,

    @XmlSerialName("subtitles") val subtitles: List<XmltvSubtitle>? = null,

    @XmlSerialName("episode-num") val episodeNumbers: List<XmltvEpisodeNum>? = null,

    @XmlSerialName("previously-shown") val previouslyShown: XmltvProgrammePreviouslyShown? = null,
)
