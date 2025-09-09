package io.github.firstred.iptvproxy.serialization.serializers

import io.github.firstred.iptvproxy.serialization.fallback
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer

object CoercedFloatSerializer : KSerializer<Float> by Float.serializer().fallback(0f)
