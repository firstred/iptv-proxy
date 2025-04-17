package io.github.firstred.iptvproxy.xmltv

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty

class XmltvRating {
    @JacksonXmlProperty(isAttribute = true)
    var system: String? = null
        private set

    private var value: String? = null

    constructor()

    constructor(system: String?, value: String?) {
        this.system = system
        this.value = value
    }

    fun setSystem(system: String?): XmltvRating {
        this.system = system
        return this
    }

    fun setValue(value: String?): XmltvRating {
        this.value = value
        return this
    }
}
