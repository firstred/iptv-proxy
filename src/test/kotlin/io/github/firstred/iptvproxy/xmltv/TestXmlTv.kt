package io.github.firstred.iptvproxy.xmltv

import io.github.firstred.iptvproxy.xmltv.XmltvUtils.createMapper
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File

object TestXmlTv {
    private val LOG: Logger = LoggerFactory.getLogger(TestXmlTv::class.java)

    @JvmStatic
    fun main(args: Array<String>) {
        try {
            val xm = createMapper()

            var doc = xm.readValue(
                File("/home/kva/projects/kvaster/iptv/epg-cbilling.xml"),
                XmltvDoc::class.java
            )
            //doc = xm.readValue(new File("/home/kva/projects/kvaster/iptv/epg-crdru.xml"), XmltvDoc.class);
            doc = xm.readValue(File("/home/kva/projects/kvaster/iptv/epg-ilooktv.xml"), XmltvDoc::class.java)
            //xm.writerWithDefaultPrettyPrinter().writeValue(new File("out.xml"), doc);
            xm.writeValue(File("tmp/out.xml"), doc)
            LOG.info("done")
        } catch (e: Exception) {
            LOG.error("error", e)
        }
    }
}
