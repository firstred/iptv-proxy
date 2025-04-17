package io.github.firstred.iptvproxy.xmltv

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement

@JacksonXmlRootElement(localName = "tv")
class XmltvDoc {
    @JacksonXmlProperty(isAttribute = true, localName = "generator-info-name")
    var generatorName: String? = null
        private set

    @JacksonXmlProperty(isAttribute = true, localName = "generator-info-url")
    var generatorUrl: String? = null
        private set

    @JacksonXmlProperty(isAttribute = true, localName = "source-info-url")
    var sourceInfoUrl: String? = null
        private set

    @JacksonXmlProperty(isAttribute = true, localName = "source-info-name")
    var sourceInfoName: String? = null
        private set

    @JacksonXmlProperty(isAttribute = true, localName = "source-info-logo")
    var sourceInfoLogo: String? = null
        private set

    @JacksonXmlProperty(localName = "channel")
    var channels: MutableList<XmltvChannel>? = null
        private set

    @JacksonXmlProperty(localName = "programme")
    var programmes: MutableList<XmltvProgramme>? = null
        private set

    constructor()

    constructor(channels: List<XmltvChannel>?, programmes: List<XmltvProgramme>?) {
        this.channels = channels?.toMutableList() ?: mutableListOf()
        this.programmes = programmes?.toMutableList() ?: mutableListOf()
    }

    fun setChannels(channels: List<XmltvChannel>?): XmltvDoc {
        this.channels = channels?.toMutableList() ?: mutableListOf()
        return this
    }

    fun setProgrammes(programmes: List<XmltvProgramme>?): XmltvDoc {
        this.programmes = programmes?.toMutableList() ?: mutableListOf()
        return this
    }

    fun setGeneratorName(generatorName: String?): XmltvDoc {
        this.generatorName = generatorName
        return this
    }

    fun setGeneratorUrl(generatorUrl: String?): XmltvDoc {
        this.generatorUrl = generatorUrl
        return this
    }

    fun setSourceInfoUrl(sourceInfoUrl: String?): XmltvDoc {
        this.sourceInfoUrl = sourceInfoUrl
        return this
    }

    fun setSourceInfoName(sourceInfoName: String?): XmltvDoc {
        this.sourceInfoName = sourceInfoName
        return this
    }

    fun setSourceInfoLogo(sourceInfoLogo: String?): XmltvDoc {
        this.sourceInfoLogo = sourceInfoLogo
        return this
    }
}
