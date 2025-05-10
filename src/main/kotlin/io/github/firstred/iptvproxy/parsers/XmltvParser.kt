package io.github.firstred.iptvproxy.parsers

import io.github.firstred.iptvproxy.dtos.xmltv.XmltvChannel
import io.github.firstred.iptvproxy.dtos.xmltv.XmltvDoc
import io.github.firstred.iptvproxy.dtos.xmltv.XmltvProgramme
import io.github.firstred.iptvproxy.serialization.xml
import nl.adaptivity.xmlutil.EventType
import nl.adaptivity.xmlutil.ExperimentalXmlUtilApi
import nl.adaptivity.xmlutil.core.KtXmlReader
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.InputStream

class XmltvParser {
    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(XmltvParser::class.java)

        @OptIn(ExperimentalXmlUtilApi::class)
        fun forEachXmltvItem(
            inputStream: InputStream,
            onHeader: (XmltvDoc) -> Unit,
            onChannel: (XmltvChannel) -> Unit,
            onProgramme: (XmltvProgramme) -> Unit,
        ) {
            val xmlReader = KtXmlReader(inputStream)
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

                        "channel" -> onChannel(xml.decodeFromReader<XmltvChannel>(xmlReader))

                        "programme" -> onProgramme(xml.decodeFromReader<XmltvProgramme>(xmlReader))
                    }
                }
            }
        }
    }
}
