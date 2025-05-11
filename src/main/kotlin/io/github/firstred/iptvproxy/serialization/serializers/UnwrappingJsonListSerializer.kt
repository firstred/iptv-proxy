package io.github.firstred.iptvproxy.serialization.serializers

import io.github.firstred.iptvproxy.dtos.xtream.XtreamEpisode
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonTransformingSerializer
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object EnsureMappedEpisodesJsonMapSerializer : JsonTransformingSerializer<Map<String, List<XtreamEpisode>>>(MapSerializer(String.serializer(), ListSerializer(XtreamEpisode.serializer()))) {
    override fun transformDeserialize(element: JsonElement): JsonElement {
        if (element is JsonArray) {
            return buildJsonObject {
                for (jsonElement in element.jsonArray) {
                    require(jsonElement is JsonArray) { "Expected JsonArray, got ${jsonElement::class}" }
                    // Get the season number from the first element of the array -- empty arrays are skipped
                    val seasonNumber = jsonElement.jsonArray.firstOrNull()?.jsonObject?.get("season")?.jsonPrimitive?.toString() ?: continue

                    put(seasonNumber, jsonElement.jsonArray)
                }
            }
        }

        return element
    }
}
