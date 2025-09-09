package io.github.firstred.iptvproxy.serialization.serializers

import io.github.firstred.iptvproxy.serialization.fallback
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer

object CoercedNullableUIntSerializer : KSerializer<UInt?> by UInt.serializer().nullable.fallback(null)
