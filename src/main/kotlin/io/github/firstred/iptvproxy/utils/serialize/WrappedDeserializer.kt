package io.github.firstred.iptvproxy.utils.serialize

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.databind.deser.ResolvableDeserializer
import com.fasterxml.jackson.databind.jsontype.TypeDeserializer
import java.io.IOException
import java.util.*

open class WrappedDeserializer<T : Any>(deserializer: JsonDeserializer<T>) :
    JsonDeserializer<T>(), ResolvableDeserializer {
    private val deserializer: JsonDeserializer<T> =
        Objects.requireNonNull(deserializer)

    @Throws(IOException::class)
    override fun deserialize(jp: JsonParser, ctxt: DeserializationContext): T {
        return afterDeserialize(deserializer.deserialize(jp, ctxt))
    }

    @Throws(IOException::class)
    override fun deserialize(jp: JsonParser, ctxt: DeserializationContext, intoValue: T): T {
        return afterDeserialize(deserializer.deserialize(jp, ctxt, intoValue))
    }

    @Throws(IOException::class)
    override fun deserializeWithType(
        jp: JsonParser, ctxt: DeserializationContext, typeDeserializer: TypeDeserializer
    ): Any {
        return afterDeserialize(deserializer.deserializeWithType(jp, ctxt, typeDeserializer) as T)
    }

    protected open fun afterDeserialize(obj: T): T {
        return obj
    }

    @Throws(JsonMappingException::class)
    override fun resolve(ctxt: DeserializationContext) {
        if (deserializer is ResolvableDeserializer) {
            (deserializer as ResolvableDeserializer).resolve(ctxt)
        }
    }
}
