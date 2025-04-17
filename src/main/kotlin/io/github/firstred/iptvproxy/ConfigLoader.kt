package io.github.firstred.iptvproxy

import com.fasterxml.jackson.annotation.JsonAutoDetect
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.introspect.VisibilityChecker
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.github.firstred.iptvproxy.utils.serialize.RelativeFileModule
import java.io.File

object ConfigLoader {
    fun <T> loadConfig(configFile: File, configClass: Class<T>?): T {
        try {
            val mapper: ObjectMapper = YAMLMapper.builder()
                .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .visibility(
                    VisibilityChecker.Std(
                        JsonAutoDetect.Visibility.NONE,
                        JsonAutoDetect.Visibility.NONE,
                        JsonAutoDetect.Visibility.NONE,
                        JsonAutoDetect.Visibility.ANY,
                        JsonAutoDetect.Visibility.ANY,
                    )
                )
                .configure(MapperFeature.AUTO_DETECT_GETTERS, false)
                .configure(MapperFeature.AUTO_DETECT_IS_GETTERS, false)
                .configure(MapperFeature.AUTO_DETECT_SETTERS, false)
                .configure(MapperFeature.AUTO_DETECT_SETTERS, false)
                .addModules(RelativeFileModule(configFile), JavaTimeModule())
                .build()

            return mapper.readValue(configFile, configClass)
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
    }
}
