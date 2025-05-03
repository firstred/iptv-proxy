package io.github.firstred.iptvproxy.dtos.xmltv

import io.github.firstred.iptvproxy.dotenv
import io.github.firstred.iptvproxy.dtos.xtream.Epg
import io.github.firstred.iptvproxy.serialization.serializers.XmltvInstantSerializer
import io.github.firstred.iptvproxy.utils.hexStringToDecimal
import io.github.firstred.iptvproxy.utils.sha256
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.format.FormatStringsInDatetimeFormats
import kotlinx.datetime.format.byUnicodePattern
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName
import java.util.*
import kotlin.text.Charsets.UTF_8

@OptIn(FormatStringsInDatetimeFormats::class)
@Serializable
@SerialName("programme")
data class XmltvProgramme(
    @XmlElement(false)
    @Serializable(with = XmltvInstantSerializer::class)
    @XmlSerialName("start") val start: Instant,

    @XmlElement(false)
    @Serializable(with = XmltvInstantSerializer::class)
    @XmlSerialName("stop") val stop: Instant,

    @XmlElement(false)
    @XmlSerialName("channel") val channel: String,

    @XmlSerialName("category") val category: List<XmltvText>? = null,

    @XmlSerialName("title") val title: XmltvText? = null,

    @XmlSerialName("sub-title") val subTitle: XmltvText? = null,

    @XmlSerialName("desc") val desc: XmltvText? = null,

    @XmlSerialName("rating") val rating: List<XmltvRating>? = null,

    @XmlSerialName("icon") val icon: XmltvIcon? = null,

    @XmlSerialName("subtitles") val subtitles: List<XmltvSubtitle>? = null,

    @XmlSerialName("episode-num") val episodeNumbers: List<XmltvEpisodeNum>? = null,

    @XmlSerialName("previously-shown") val previouslyShown: List<XmltvProgrammePreviouslyShown>? = null,

    @XmlSerialName("audio") val audio: XmltvAudio? = null,

    val server: String? = null,
) {
    fun toEpg() = Epg(
        start = start.toLocalDateTime(TimeZone.of(dotenv.get("TZ") ?: "UTC")).format(LocalDateTime.Format {
            byUnicodePattern("yyyy-MM-dd HH:mm:ss")
        }),
        end = stop.toLocalDateTime(TimeZone.of(dotenv.get("TZ") ?: "UTC")).format(LocalDateTime.Format {
            byUnicodePattern("yyyy-MM-dd HH:mm:ss")
        }),
        channelId = channel,
        externalId = "$channel-$server".sha256().hexStringToDecimal().toString(),
        epgId = "$channel-$server".sha256().hexStringToDecimal().toString(),
        startTimestamp = (start.toEpochMilliseconds() / 1000).toString(),
        stopTimestamp = (stop.toEpochMilliseconds() / 1000).toString(),
        title = Base64.getEncoder().encodeToString( "${title?.text}".toByteArray(UTF_8)),
        description = Base64.getEncoder().encodeToString( "${desc?.text}".toByteArray(UTF_8)),
        lang = desc?.language ?: "",
        nowPlaying = if (Clock.System.now() > start && Clock.System.now() < stop) 1 else 0,
        hasArchive = 0,
    )
}
