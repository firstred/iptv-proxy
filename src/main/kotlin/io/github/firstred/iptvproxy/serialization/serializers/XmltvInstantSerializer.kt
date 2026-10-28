package io.github.firstred.iptvproxy.serialization.serializers

import kotlinx.datetime.UtcOffset
import kotlinx.datetime.format
import kotlinx.datetime.format.DateTimeComponents
import kotlinx.datetime.format.FormatStringsInDatetimeFormats
import kotlinx.datetime.format.alternativeParsing
import kotlinx.datetime.format.byUnicodePattern
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.DateTimeException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

@OptIn(FormatStringsInDatetimeFormats::class)
object XmltvInstantSerializer: KSerializer<Instant> {
    val LOG: Logger = LoggerFactory.getLogger(XmltvInstantSerializer::class.java)

    private val xmltvFormat = DateTimeComponents.Format {
        byUnicodePattern("yyyyMMddHHmmss")
        alternativeParsing(
            { byUnicodePattern("Z") },
            { byUnicodePattern(" Z") }
        ) {}
    }

    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("XmltvInstant", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): Instant = parse(decoder.decodeString())

    /**
     * Parses an XMLTV timestamp
     *
     * Rolled over time values that some providers emit (hour 24, minute 60 or second 60) are carried over to the next
     * unit, e.g. `20261028026000 +0000` is parsed as 03:00 UTC.
     *
     * @throws SerializationException containing the actual value when the timestamp cannot be parsed
     */
    fun parse(value: String): Instant = try {
        val components = xmltvFormat.parse(value.trim())
        if (components.offsetHours == null) {
            components.setOffset(UtcOffset.ZERO)
        }

        var carryOver = Duration.ZERO
        if (60 == components.second) {
            components.second = 0
            carryOver += 1.minutes
        }
        if (60 == components.minute) {
            components.minute = 0
            carryOver += 1.hours
        }
        if (24 == components.hour) {
            components.hour = 0
            carryOver += 1.days
        }

        (components.toInstantUsingOffset() + carryOver).also {
            if (carryOver.isPositive()) LOG.debug("Normalized rolled over XMLTV timestamp \"{}\" to {}", value, it)
        }
    } catch (e: IllegalArgumentException) {
        val reason = (e.cause as? DateTimeException)?.message ?: e.message
        throw SerializationException("Unable to parse XMLTV timestamp \"$value\": $reason", e)
    }

    override fun serialize(encoder: Encoder, value: Instant) = encoder.encodeString(
        value.format(DateTimeComponents.Format {
            byUnicodePattern("yyyyMMddHHmmss Z")
        })
    )
}
