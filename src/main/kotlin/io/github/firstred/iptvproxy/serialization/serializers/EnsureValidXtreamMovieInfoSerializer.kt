package io.github.firstred.iptvproxy.serialization.serializers

import io.github.firstred.iptvproxy.dtos.xtream.XtreamMovieInfo
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonTransformingSerializer

object EnsureValidXtreamMovieInfoSerializer : JsonTransformingSerializer<XtreamMovieInfo>(XtreamMovieInfo.serializer()) {
    override fun transformDeserialize(element: JsonElement): JsonElement {
        if (element is JsonArray) return JsonObject(mapOf())

        return element
    }
}
