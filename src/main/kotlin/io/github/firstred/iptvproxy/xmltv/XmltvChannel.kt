package io.github.firstred.iptvproxy.xmltv

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty

class XmltvChannel {
    @JacksonXmlProperty(isAttribute = true, localName = "id")
    var id: String? = null
        private set

    @JacksonXmlProperty(localName = "display-name")
    var displayNames: List<XmltvText>? = null
        private set

    var icon: XmltvIcon? = null
        private set

    constructor()

    constructor(id: String?, displayNames: List<XmltvText>?, icon: XmltvIcon?) {
        this.id = id
        this.displayNames = displayNames
        this.icon = icon
    }

    constructor(id: String?, displayName: XmltvText, icon: XmltvIcon?) : this(id, listOf<XmltvText>(displayName), icon)

    fun setId(id: String?): XmltvChannel {
        this.id = id
        return this
    }

    fun setDisplayNames(displayNames: List<XmltvText>?): XmltvChannel {
        this.displayNames = displayNames
        return this
    }

    fun setIcon(icon: XmltvIcon?): XmltvChannel {
        this.icon = icon
        return this
    }
}
