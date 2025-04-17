package io.github.firstred.iptvproxy.utils.serialize

import com.fasterxml.jackson.core.Version
import com.fasterxml.jackson.databind.BeanDescription
import com.fasterxml.jackson.databind.DeserializationConfig
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.Module
import com.fasterxml.jackson.databind.deser.BeanDeserializerModifier
import java.io.File

class RelativeFileModule(private val base: File) : Module() {
    private inner class RelativeFileDeserializer(deserializer: JsonDeserializer<File>) :
        WrappedDeserializer<File>(deserializer) {
        override fun afterDeserialize(obj: File): File {
            if (obj.isAbsolute) return obj

            return File(base, obj.path)
        }
    }


    private inner class RealtiveFileDeserializerModifier : BeanDeserializerModifier() {
        override fun modifyDeserializer(
            config: DeserializationConfig, beanDesc: BeanDescription, deserializer: JsonDeserializer<*>
        ): JsonDeserializer<*> {
            if (beanDesc.beanClass.isAssignableFrom(File::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return RelativeFileDeserializer(deserializer as JsonDeserializer<File>)
            }

            return deserializer
        }
    }

    override fun getModuleName(): String {
        return javaClass.simpleName
    }

    override fun version(): Version {
        return Version.unknownVersion()
    }

    override fun setupModule(context: SetupContext) {
        context.addBeanDeserializerModifier(RealtiveFileDeserializerModifier())
    }
}
