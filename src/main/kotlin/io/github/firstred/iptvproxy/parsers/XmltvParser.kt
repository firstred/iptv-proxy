package io.github.firstred.iptvproxy.parsers

import io.github.firstred.iptvproxy.dtos.xmltv.XmltvChannel
import io.github.firstred.iptvproxy.dtos.xmltv.XmltvDoc
import io.github.firstred.iptvproxy.dtos.xmltv.XmltvProgramme
import io.github.firstred.iptvproxy.exceptions.XmltvFeedException
import io.github.firstred.iptvproxy.serialization.xml
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import nl.adaptivity.xmlutil.EventType
import nl.adaptivity.xmlutil.ExperimentalXmlUtilApi
import nl.adaptivity.xmlutil.XmlException
import nl.adaptivity.xmlutil.core.KtXmlReader
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.IOException
import java.io.InputStream

data class XmltvParseResult(
    val channels: Int,
    val programmes: Int,
    val skippedChannels: Int,
    val skippedProgrammes: Int,
) {
    val importedItems: Int get() = channels + programmes
    val skippedItems: Int get() = skippedChannels + skippedProgrammes
}

class XmltvParser {
    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(XmltvParser::class.java)
        private const val MAX_WARNED_SKIPPED_ITEMS = 10

        @OptIn(ExperimentalXmlUtilApi::class)
        fun forEachXmltvItem(
            inputStream: InputStream,
            onHeader: (XmltvDoc) -> Unit,
            onChannel: (XmltvChannel) -> Unit,
            onProgramme: (XmltvProgramme) -> Unit,
            sourceName: String? = null,
        ): XmltvParseResult {
            val xmlReader = KtXmlReader(inputStream)
            var channels = 0
            var programmes = 0
            var skippedChannels = 0
            var skippedProgrammes = 0
            var firstError: Exception? = null

            while (xmlReader.hasNext()) {
                xmlReader.next()

                if (EventType.START_ELEMENT == xmlReader.eventType) {
                    when (xmlReader.localName) {
                        "tv" -> {
                            val xmltvDoc = XmltvDoc(
                                sourceInfoName = xmlReader.getAttributeValue(null, "source-info-name"),
                                sourceInfoUrl = xmlReader.getAttributeValue(null, "source-info-url"),
                                sourceInfoLogo = xmlReader.getAttributeValue(null, "source-info-logo"),
                                generatorInfoName = xmlReader.getAttributeValue(null, "generator-info-name"),
                                generatorInfoUrl = xmlReader.getAttributeValue(null, "generator-info-url"),
                            )
                            onHeader(xmltvDoc)
                        }

                        "channel" -> {
                            val itemDepth = xmlReader.depth
                            val attributes = "id=" + xmlReader.getAttributeValue(null, "id")
                            val channel = try {
                                xml.decodeFromReader<XmltvChannel>(xmlReader)
                            } catch (e: Exception) {
                                if (!isRecoverableItemDecodeFailure(e)) throw e
                                skippedChannels++
                                if (null == firstError) firstError = e
                                logSkippedItem("channel", attributes, e, sourceName, skippedChannels + skippedProgrammes)
                                skipToEndElement(xmlReader, itemDepth)
                                null
                            }

                            if (null != channel) {
                                onChannel(channel)
                                channels++
                            }
                        }

                        "programme" -> {
                            val itemDepth = xmlReader.depth
                            val attributes = "channel=${xmlReader.getAttributeValue(null, "channel")}, " +
                                "start=${xmlReader.getAttributeValue(null, "start")}, " +
                                "stop=" + xmlReader.getAttributeValue(null, "stop")
                            val programme = try {
                                xml.decodeFromReader<XmltvProgramme>(xmlReader)
                            } catch (e: Exception) {
                                if (!isRecoverableItemDecodeFailure(e)) throw e
                                skippedProgrammes++
                                if (null == firstError) firstError = e
                                logSkippedItem("programme", attributes, e, sourceName, skippedChannels + skippedProgrammes)
                                skipToEndElement(xmlReader, itemDepth)
                                null
                            }

                            if (null != programme) {
                                onProgramme(programme)
                                programmes++
                            }
                        }
                    }
                }
            }

            val result = XmltvParseResult(channels, programmes, skippedChannels, skippedProgrammes)
            if (result.skippedItems > 0) {
                LOG.warn(
                    "Skipped {} broken xmltv items ({} imported) from {}",
                    result.skippedItems,
                    result.importedItems,
                    sourceName ?: "XMLTV feed",
                )
            }
            if (result.skippedItems > 0 && result.importedItems == 0) {
                throw XmltvFeedException(sourceName, result.skippedItems, firstError!!)
            }
            return result
        }

        private fun isRecoverableItemDecodeFailure(exception: Exception): Boolean {
            if (exception is IOException && exception !is XmlException) return false

            var isSerializationFailure = false
            var cause: Throwable? = exception
            while (null != cause) {
                if (cause is CancellationException) return false
                if (cause !== exception && cause is IOException) return false
                if (cause is SerializationException) isSerializationFailure = true
                cause = cause.cause
            }
            return isSerializationFailure
        }

        @OptIn(ExperimentalXmlUtilApi::class)
        private fun skipToEndElement(xmlReader: KtXmlReader, itemDepth: Int) {
            if (EventType.END_ELEMENT == xmlReader.eventType && xmlReader.depth == itemDepth) return

            while (xmlReader.hasNext()) {
                xmlReader.next()
                if (EventType.END_ELEMENT == xmlReader.eventType && xmlReader.depth == itemDepth) return
            }
        }

        private fun logSkippedItem(
            element: String,
            attributes: String,
            exception: Exception,
            sourceName: String?,
            skippedItems: Int,
        ) {
            val message = "Skipping broken xmltv $element item from ${sourceName ?: "XMLTV feed"} ($attributes): ${exception.message}"
            if (skippedItems <= MAX_WARNED_SKIPPED_ITEMS) {
                LOG.warn(message)
            } else {
                LOG.debug(message)
            }
        }
    }
}
