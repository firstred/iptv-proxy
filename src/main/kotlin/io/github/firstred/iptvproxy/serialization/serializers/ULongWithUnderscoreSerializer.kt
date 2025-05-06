package io.github.firstred.iptvproxy.serialization.serializers

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object ULongWithUnderscoreSerializer: KSerializer<ULong> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("ULong", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): ULong = decoder.decodeString().replace("_", "").toULong()
    override fun serialize(encoder: Encoder, value: ULong) = encoder.encodeString(value.toString())
}
