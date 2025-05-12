package io.github.firstred.iptvproxy.serialization.serializers

import io.github.firstred.iptvproxy.dtos.xtream.XtreamEpisodeListing
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonTransformingSerializer

object EnsureValidXtreamSeriesInfoSerializer : JsonTransformingSerializer<XtreamEpisodeListing>(XtreamEpisodeListing.serializer()) {
    override fun transformDeserialize(element: JsonElement): JsonElement {
        if (element is JsonArray) return JsonObject(mapOf())

        return element
    }
}
