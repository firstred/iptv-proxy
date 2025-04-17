package io.github.firstred.iptvproxy.xmltv

import com.fasterxml.jackson.annotation.JsonAutoDetect
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.introspect.VisibilityChecker
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.BufferedOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

object XmltvUtils {
    private val LOG: Logger = LoggerFactory.getLogger(XmltvUtils::class.java)

    val xmltvMapper: XmlMapper = createMapper()

    @JvmStatic
    fun createMapper(): XmlMapper {
        return XmlMapper.builder()
            .configure(MapperFeature.AUTO_DETECT_GETTERS, false)
            .configure(MapperFeature.AUTO_DETECT_IS_GETTERS, false)
            .configure(MapperFeature.AUTO_DETECT_SETTERS, false)
            .configure(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE, false)
            .defaultUseWrapper(false)
            .addModule(JavaTimeModule())
            .visibility(
                VisibilityChecker.Std(
                    JsonAutoDetect.Visibility.NONE,
                    JsonAutoDetect.Visibility.NONE,
                    JsonAutoDetect.Visibility.NONE,
                    JsonAutoDetect.Visibility.ANY,
                    JsonAutoDetect.Visibility.ANY
                )
            )
            .serializationInclusion(JsonInclude.Include.NON_NULL)
            .build()
    }

    fun parseXmltv(data: ByteArray): XmltvDoc? {
        try {
            openStream(data).use { inputStream ->
                return xmltvMapper.readValue(
                    inputStream,
                    XmltvDoc::class.java
                )
            }
        } catch (e: IOException) {
            LOG.error("error parsing xmltv data")
            return null
        }
    }

    @Throws(IOException::class)
    private fun openStream(data: ByteArray): InputStream {
        var inputStream: InputStream = ByteArrayInputStream(data)
        if (data.size >= 2 && data[0] == 0x1f.toByte() && data[1] == 0x8b.toByte()) {
            inputStream = GZIPInputStream(inputStream)
        }

        return inputStream
    }

    fun writeXmltv(xmltv: XmltvDoc?): ByteArray {
        try {
            val byteArrayOutputStream = ByteArrayOutputStream()

            GZIPOutputStream(byteArrayOutputStream).use { gos ->
                BufferedOutputStream(gos).use { bufferedOutputStream ->
                    bufferedOutputStream.write(
                        "<?xml version=\"1.0\" encoding=\"UTF-8\"?><!DOCTYPE tv SYSTEM \"xmltv.dtd\">\n".toByteArray(
                            StandardCharsets.UTF_8
                        )
                    )
                    xmltvMapper.writeValue(bufferedOutputStream, xmltv)
                    //xmltvMapper.writerWithDefaultPrettyPrinter().writeValue(bbos, xmltv);
                    bufferedOutputStream.flush()
                }
            }
            return byteArrayOutputStream.toByteArray()
        } catch (e: IOException) {
            LOG.error("error serializing xmltv data", e)
            throw RuntimeException(e)
        }
    }
}
