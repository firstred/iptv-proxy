package io.github.firstred.iptvproxy.xmltv

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty

class XmltvIcon {
    @JacksonXmlProperty(isAttribute = true)
    var src: String? = null

    constructor()

    constructor(src: String?) {
        this.src = src
    }

    fun setSrc(src: String?): XmltvIcon {
        this.src = src
        return this
    }
}
