package io.github.firstred.iptvproxy.serialization.serializers

import kotlinx.datetime.Instant
import kotlinx.datetime.format
import kotlinx.datetime.format.DateTimeComponents
import kotlinx.datetime.format.FormatStringsInDatetimeFormats
import kotlinx.datetime.format.byUnicodePattern
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@OptIn(FormatStringsInDatetimeFormats::class)
object XmltvInstantSerializer: KSerializer<Instant> {
    val LOG: Logger = LoggerFactory.getLogger(XmltvInstantSerializer::class.java)

    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("XmltvInstant", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): Instant {
        return try {
            Instant.parse(decoder.decodeString(), DateTimeComponents.Format {
                byUnicodePattern("yyyyMMddHHmmss Z")
            })
        } catch (e: Throwable) {
            Instant.parse(decoder.decodeString(), DateTimeComponents.Format {
                byUnicodePattern("yyyyMMddHHmmss")
            })
        }
    }
    override fun serialize(encoder: Encoder, value: Instant) = encoder.encodeString(
        value.format(DateTimeComponents.Format {
            byUnicodePattern("yyyyMMddHHmmss Z")
        })
    )
}
