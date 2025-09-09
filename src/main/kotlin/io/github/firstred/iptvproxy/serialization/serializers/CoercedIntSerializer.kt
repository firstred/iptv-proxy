package io.github.firstred.iptvproxy.serialization.serializers

import io.github.firstred.iptvproxy.serialization.fallback
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer

object CoercedIntSerializer : KSerializer<Int> by Int.serializer().fallback(0)
