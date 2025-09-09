package io.github.firstred.iptvproxy.serialization.serializers

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.nullable
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object NullableIntSerializer : KSerializer<Int?> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("NullableIntSerializer", PrimitiveKind.INT).nullable
    private val delegate = Int.serializer().nullable

    override fun serialize(encoder: Encoder, value: Int?) {
        if (value is Int) {
            delegate.serialize(encoder, value)
        } else {
            delegate.serialize(encoder, null)
        }
    }

    override fun deserialize(decoder: Decoder): Int? {
        return try {
            decoder.decodeInt()
        } catch (_: Throwable) {
            null
        }
    }
}
