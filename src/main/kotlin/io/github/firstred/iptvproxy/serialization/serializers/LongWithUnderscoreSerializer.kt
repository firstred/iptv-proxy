package io.github.firstred.iptvproxy.serialization.serializers

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object LongWithUnderscoreSerializer: KSerializer<Long> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Long", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): Long = decoder.decodeString().replace("_", "").toLong()
    override fun serialize(encoder: Encoder, value: Long) = encoder.encodeString(value.toString())
}
