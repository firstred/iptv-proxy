package io.github.firstred.iptvproxy.serialization.serializers

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object UIntWithUnderscoreSerializer: KSerializer<UInt> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("UInt", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): UInt = decoder.decodeString().replace("_", "").toUInt()
    override fun serialize(encoder: Encoder, value: UInt) = encoder.encodeString(value.toString())
}
