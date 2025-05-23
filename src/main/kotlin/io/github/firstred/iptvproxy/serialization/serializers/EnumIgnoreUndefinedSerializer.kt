package io.github.firstred.iptvproxy.serialization.serializers

import io.github.firstred.iptvproxy.config
import io.github.firstred.iptvproxy.exceptions.UndefinedEnumSerializerException
import io.sentry.Sentry
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

abstract class EnumIgnoreUndefinedSerializer<T : Enum<T>>(values: Array<out T>, private val defaultValue: T) : KSerializer<T> {
    // Alternative to taking values in param, take clazz: Class<T>
    // - private val values = clazz.enumConstants
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor(values.first()::class.qualifiedName!!, PrimitiveKind.STRING)
    // Build maps for faster parsing, used @SerialName annotation if present, fall back to name
    private val lookup = values.associateBy({ it }, { it.serialName })
    private val revLookup = values.associateBy { it.serialName }

    private val Enum<T>.serialName: String
        get() = this::class.java.getField(this.name).getAnnotation(SerialName::class.java)?.value ?: name

    override fun serialize(encoder: Encoder, value: T) {
        encoder.encodeString(lookup.getValue(value))
    }

    override fun deserialize(decoder: Decoder): T {
        // only run 'decoder.decodeString()' once
        val serialName = decoder.decodeString()
        val value = revLookup[serialName]
        return if (null == value) {
            if (!config.sentry?.dsn.isNullOrBlank()) {
                Sentry.captureException(
                    UndefinedEnumSerializerException(this::class.java.simpleName, serialName),
                )
            }
            defaultValue
        } else {
            value
        }
    }
}
