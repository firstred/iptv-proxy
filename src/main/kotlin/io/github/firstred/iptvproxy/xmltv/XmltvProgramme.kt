package io.github.firstred.iptvproxy.xmltv

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty
import java.time.ZonedDateTime

class XmltvProgramme {
    @JacksonXmlProperty(isAttribute = true)
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyyMMddHHmmss Z")
    var start: ZonedDateTime? = null
        private set

    @JacksonXmlProperty(isAttribute = true)
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyyMMddHHmmss Z")
    var stop: ZonedDateTime? = null
        private set

    @JacksonXmlProperty(isAttribute = true)
    var channel: String? = null
        private set

    var category: XmltvText? = null
        private set

    var title: XmltvText? = null
        private set

    var desc: XmltvText? = null
        private set

    var rating: XmltvRating? = null
        private set

    var icon: XmltvIcon? = null
        private set

    constructor()

    constructor(p: XmltvProgramme) {
        this.start = p.start
        this.stop = p.stop
        this.channel = p.channel
        this.category = p.category
        this.title = p.title
        this.desc = p.desc
        this.rating = p.rating
        this.icon = p.icon
    }

    constructor(channel: String?, start: ZonedDateTime?, stop: ZonedDateTime?) {
        this.channel = channel
        this.start = start
        this.stop = stop
    }

    fun copy(): XmltvProgramme {
        return XmltvProgramme(this)
    }

    fun setChannel(channel: String?): XmltvProgramme {
        this.channel = channel
        return this
    }

    fun setStart(start: ZonedDateTime?): XmltvProgramme {
        this.start = start
        return this
    }

    fun setStop(stop: ZonedDateTime?): XmltvProgramme {
        this.stop = stop
        return this
    }

    fun setCategory(category: XmltvText?): XmltvProgramme {
        this.category = category
        return this
    }

    fun setTitle(title: XmltvText?): XmltvProgramme {
        this.title = title
        return this
    }

    fun setDesc(desc: XmltvText?): XmltvProgramme {
        this.desc = desc
        return this
    }

    fun setRating(rating: XmltvRating?): XmltvProgramme {
        this.rating = rating
        return this
    }

    fun setIcon(icon: XmltvIcon?): XmltvProgramme {
        this.icon = icon
        return this
    }
}
