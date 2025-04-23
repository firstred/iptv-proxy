package io.github.firstred.iptvproxy.serialization.serializers

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object IntWithUnderscoreSerializer: KSerializer<Int> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Int", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): Int = decoder.decodeString().replace("_", "").toInt()
    override fun serialize(encoder: Encoder, value: Int) = encoder.encodeString(value.toString())
}
