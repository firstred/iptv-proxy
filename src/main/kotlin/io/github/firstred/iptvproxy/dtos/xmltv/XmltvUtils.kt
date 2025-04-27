package io.github.firstred.iptvproxy.dtos.xmltv

import io.github.firstred.iptvproxy.config
import io.github.firstred.iptvproxy.serialization.xml
import nl.adaptivity.xmlutil.XmlStreaming
import nl.adaptivity.xmlutil.xmlStreaming
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.nio.charset.StandardCharsets.UTF_8
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

class XmltvUtils {
    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(XmltvUtils::class.java)

        fun parseXmltv(data: InputStreamReader): XmltvDoc {
            return xml.decodeFromReader<XmltvDoc>(xmlStreaming.newReader(data))
        }

        fun parseXmltv(data: InputStream): XmltvDoc {
            return xml.decodeFromReader<XmltvDoc>(xmlStreaming.newReader(InputStreamReader(data, UTF_8)))
        }

        @Deprecated("Use parseXmltv(data: InputStream) instead")
        fun parseXmltv(data: ByteArray): XmltvDoc {
            val input = InputStreamReader(ByteArrayInputStream(data), UTF_8)
            return parseXmltv(input)
        }

        @Throws(IOException::class)
        fun xmltvInputStream(
            compressed: Boolean = true,
            file: File = getCachedXmltvFile(compressed = true),
        ): InputStream {
            val inputStream = file.inputStream()
            val peekInputStream = File(file.toURI()).inputStream()
            val data = peekInputStream.use { it.readNBytes(2) }
            val isFileCompressed = data.size >= 2 && data[0] == 0x1f.toByte() && data[1] == 0x8b.toByte()

            if (compressed && !isFileCompressed) {
                val pipedOutputStream = PipedOutputStream()
                val pipedInputStream = PipedInputStream(pipedOutputStream)

                val gzipOutput = GZIPOutputStream(pipedOutputStream, true)
                inputStream.use { it.copyTo(gzipOutput) }

                return pipedInputStream
            } else if (!compressed && isFileCompressed) {
                return GZIPInputStream(inputStream)
            }

            return inputStream
        }

        fun writeXmltv(
            xmltv: XmltvDoc,
            compressed: Boolean = true,
            output: OutputStream = getCachedXmltvFile(compressed).outputStream(),
        ) {
            try {
                val outputStream = if (compressed) {
                    GZIPOutputStream(output).bufferedWriter(UTF_8)
                } else {
                    output.bufferedWriter(UTF_8)
                }
                outputStream.use {
                    outputStream.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?><!DOCTYPE tv SYSTEM \"xmltv.dtd\">\n")

                    val writer = XmlStreaming.newWriter(outputStream)
                    xml.encodeToWriter(writer, XmltvDoc.serializer(), xmltv)
                }
            } catch (e: IOException) {
                LOG.error("error serializing EPG data", e)
                throw RuntimeException(e)
            }
        }

        fun getCachedXmltvFile(compressed: Boolean = true): File {
            return File(config.getMiscCacheDirectory() + "/xmltv.xml" + if (compressed) ".gz" else "")
        }
    }
}
