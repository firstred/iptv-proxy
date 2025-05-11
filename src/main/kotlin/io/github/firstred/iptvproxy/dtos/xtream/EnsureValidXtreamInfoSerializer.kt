package io.github.firstred.iptvproxy.dtos.xtream

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonTransformingSerializer

object EnsureValidXtreamInfoSerializer : JsonTransformingSerializer<XtreamMovieInfo>(XtreamMovieInfo.serializer()) {
    override fun transformDeserialize(element: JsonElement): JsonElement {
        if (element is JsonArray) return JsonNull

        return element
    }
}
