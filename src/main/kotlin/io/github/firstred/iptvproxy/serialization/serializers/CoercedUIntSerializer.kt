package io.github.firstred.iptvproxy.serialization.serializers

import io.github.firstred.iptvproxy.serialization.fallback
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer

object CoercedUIntSerializer : KSerializer<UInt> by UInt.serializer().fallback(0u)
