package io.github.firstred.iptvproxy.serialization.serializers

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object FloatWithUnderscoreSerializer: KSerializer<Float> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Float", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): Float = decoder.decodeString().replace("_", "").toFloat()
    override fun serialize(encoder: Encoder, value: Float) = encoder.encodeString(value.toString())
}
