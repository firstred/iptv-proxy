package io.github.firstred.iptvproxy.config

class IptvProxyConfig protected constructor() {
    var host: String = "127.0.0.1"
        private set
    var port: Int = 8080
        private set
    var baseUrl: String? = null
        private set
    var forwardedPass: String? = null
        private set
    var tokenSalt: String? = null
        private set
    var servers: List<IptvServerConfig>? = null
        private set
    var allowAnonymous: Boolean = true
        private set
    var users: Set<String> = HashSet()
        private set
    var channelsTimeoutSec: Long = 5
        private set
    var channelsTotalTimeoutSec: Long = 60
        private set
    var channelsRetryDelayMs: Long = 1000
        private set
    var xmltvTimeoutSec: Long = 30
        private set
    var xmltvTotalTimeoutSec: Long = 120
        private set
    var xmltvRetryDelayMs: Long = 1000
        private set
    var useHttp2: Boolean = false
        private set

    class Builder {
        private val c = IptvProxyConfig()

        fun build(): IptvProxyConfig {
            return c
        }

        fun host(host: String): Builder {
            c.host = host
            return this
        }

        fun port(port: Int): Builder {
            c.port = port
            return this
        }

        fun baseUrl(baseUrl: String?): Builder {
            c.baseUrl = baseUrl
            return this
        }

        fun forwardedPass(forwardedPass: String?): Builder {
            c.forwardedPass = forwardedPass
            return this
        }

        fun tokenSalt(tokenSalt: String?): Builder {
            c.tokenSalt = tokenSalt
            return this
        }

        fun servers(servers: Collection<IptvServerConfig>): Builder {
            c.servers = ArrayList(servers)
            return this
        }

        fun allowAnonymous(allowAnonymous: Boolean): Builder {
            c.allowAnonymous = allowAnonymous
            return this
        }

        fun users(users: Collection<String>): Builder {
            c.users = HashSet(users)
            return this
        }

        fun channelsTimeoutSec(channelsTimeoutSec: Long): Builder {
            c.channelsTimeoutSec = channelsTimeoutSec
            return this
        }

        fun channelsTotalTimeoutSec(channelsTotalTimeoutSec: Long): Builder {
            c.channelsTotalTimeoutSec = channelsTotalTimeoutSec
            return this
        }

        fun channelsRetryDelayMs(channelsRetryDelayMs: Long): Builder {
            c.channelsRetryDelayMs = channelsRetryDelayMs
            return this
        }

        fun xmltvTimeoutSec(xmltvTimeoutSec: Long): Builder {
            c.xmltvTimeoutSec = xmltvTimeoutSec
            return this
        }

        fun xmltvTotalTimeoutSec(xmltvTotalTimeoutSec: Long): Builder {
            c.xmltvTotalTimeoutSec = xmltvTotalTimeoutSec
            return this
        }

        fun xmltvRetryDelayMs(xmltvRetryDelayMs: Long): Builder {
            c.xmltvRetryDelayMs = xmltvRetryDelayMs
            return this
        }

        fun useHttp2(useHttp2: Boolean): Builder {
            c.useHttp2 = useHttp2
            return this
        }
    }

    companion object {
        fun newBuilder(): Builder {
            return Builder()
        }
    }
}
