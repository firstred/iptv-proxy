package io.github.firstred.iptvproxy.serialization

import kotlinx.serialization.KSerializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.json.JsonDecoder

fun <T> KSerializer<T>.fallback(fallbackValue: T) = object : KSerializer<T> by this {
    override fun deserialize(decoder: Decoder): T {
        check(decoder is JsonDecoder) {
            "This deserializer only supports deserializing JSON"
        }
        return try {
            decoder.json.decodeFromJsonElement(this@fallback, decoder.decodeJsonElement())
        } catch (_: Throwable) {
            // Deserialization failed! Falling back, but you should
            // log the exception in case it shouldn't be happening.
            fallbackValue
        }
    }
}
