package io.github.firstred.iptvproxy.serialization.serializers

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object DoubleWithUnderscoreSerializer: KSerializer<Double> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Double", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): Double = decoder.decodeString().replace("_", "").toDouble()
    override fun serialize(encoder: Encoder, value: Double) = encoder.encodeString(value.toString())
}
