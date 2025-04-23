package io.github.firstred.iptvproxy.serialization

import com.charleskorn.kaml.AnchorsAndAliases
import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlNamingStrategy
import kotlinx.serialization.json.Json
import nl.adaptivity.xmlutil.ExperimentalXmlUtilApi
import nl.adaptivity.xmlutil.serialization.XML
import nl.adaptivity.xmlutil.serialization.XmlConfig.Companion.IGNORING_UNKNOWN_CHILD_HANDLER

val json = Json {
    prettyPrint = true
    ignoreUnknownKeys = true
    isLenient = true
    encodeDefaults = true
    explicitNulls = false
    useArrayPolymorphism = true
}

val yaml = Yaml(
    configuration = Yaml.default.configuration.copy(
        encodeDefaults = true,
        strictMode = false,
        yamlNamingStrategy = YamlNamingStrategy.SnakeCase,
        anchorsAndAliases = AnchorsAndAliases.Permitted(),
        extensionDefinitionPrefix = "x-",
    ),
)

@OptIn(ExperimentalXmlUtilApi::class)
val xml = XML {
    isUnchecked = true
    isCachingEnabled = true
    repairNamespaces = true

    defaultPolicy {
        autoPolymorphic = true
        unknownChildHandler = IGNORING_UNKNOWN_CHILD_HANDLER
    }
}
