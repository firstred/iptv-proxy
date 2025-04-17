package io.github.firstred.iptvproxy.xmltv

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlText

class XmltvText {
    @JacksonXmlProperty(isAttribute = true, localName = "lang")
    var language: String? = null
        private set

    @JacksonXmlText
    var text: String? = null
        private set

    constructor()

    @JvmOverloads
    constructor(text: String?, language: String? = null) {
        this.text = text
        this.language = language
    }

    fun setText(text: String?): XmltvText {
        this.text = text
        return this
    }

    fun setLanguage(language: String?): XmltvText {
        this.language = language
        return this
    }
}
