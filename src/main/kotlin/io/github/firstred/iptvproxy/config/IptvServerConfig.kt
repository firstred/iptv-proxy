package io.github.firstred.iptvproxy.config

import java.time.Duration
import java.util.regex.Pattern

class IptvServerConfig private constructor() {
    var name: String? = null
        private set
    var connections: List<IptvConnectionConfig>? = null
        private set
    var xmltvUrl: String? = null
        private set
    var xmltvBefore: Duration? = null
        private set
    var xmltvAfter: Duration? = null
        private set
    var sendUser: Boolean = false
        private set
    var proxyStream: Boolean = true
        private set
    var channelFailedMs: Long = 0
        private set
    var infoTimeoutMs: Long = 1000
        private set
    var infoTotalTimeoutMs: Long = 2000
        private set
    var infoRetryDelayMs: Long = 100
        private set
    var catchupTimeoutMs: Long = 1000
        private set
    var catchupTotalTimeoutMs: Long = 2000
        private set
    var catchupRetryDelayMs: Long = 100
        private set
    var streamStartTimeoutMs: Long = 1000
        private set
    var streamReadTimeoutMs: Long = 1000
        private set

    var groupFilters: List<Pattern> = emptyList()
        private set

    class Builder {
        private val c = IptvServerConfig()

        fun build(): IptvServerConfig {
            return c
        }

        fun name(name: String?): Builder {
            c.name = name
            return this
        }

        fun connections(connections: Collection<IptvConnectionConfig>): Builder {
            c.connections = ArrayList(connections)
            return this
        }

        fun xmltvUrl(xmltvUrl: String?): Builder {
            c.xmltvUrl = xmltvUrl
            return this
        }

        fun xmltvBefore(xmltvBefore: Duration?): Builder {
            c.xmltvBefore = xmltvBefore
            return this
        }

        fun xmltvAfter(xmltvAfter: Duration?): Builder {
            c.xmltvAfter = xmltvAfter
            return this
        }

        fun sendUser(sendUser: Boolean): Builder {
            c.sendUser = sendUser
            return this
        }

        fun proxyStream(proxyStream: Boolean): Builder {
            c.proxyStream = proxyStream
            return this
        }

        fun channelFailedMs(channelFailedMs: Long): Builder {
            c.channelFailedMs = channelFailedMs
            return this
        }

        fun infoTimeoutMs(infoTimeoutMs: Long): Builder {
            c.infoTimeoutMs = infoTimeoutMs
            return this
        }

        fun infoTotalTimeoutMs(infoTotalTimeoutMs: Long): Builder {
            c.infoTotalTimeoutMs = infoTotalTimeoutMs
            return this
        }

        fun infoRetryDelayMs(infoRetryDelayMs: Long): Builder {
            c.infoRetryDelayMs = infoRetryDelayMs
            return this
        }

        fun catchupTimeoutMs(catchupTimeoutMs: Long): Builder {
            c.catchupTimeoutMs = catchupTimeoutMs
            return this
        }

        fun catchupTotalTimeoutMs(catchupTotalTimeoutMs: Long): Builder {
            c.catchupTotalTimeoutMs = catchupTotalTimeoutMs
            return this
        }

        fun catchupRetryDelayMs(catchupRetryDelayMs: Long): Builder {
            c.catchupRetryDelayMs = catchupRetryDelayMs
            return this
        }

        fun streamStartTimeoutMs(streamStartTimeoutMs: Long): Builder {
            c.streamStartTimeoutMs = streamStartTimeoutMs
            return this
        }

        fun streamReadTimeoutMs(streamReadTimeoutMs: Long): Builder {
            c.streamReadTimeoutMs = streamReadTimeoutMs
            return this
        }

        fun groupFilters(groupFilters: Collection<Pattern>): Builder {
            c.groupFilters = ArrayList(groupFilters)
            return this
        }
    }

    companion object {
        fun newBuilder(): Builder {
            return Builder()
        }
    }
}
