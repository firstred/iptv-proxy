package io.github.firstred.iptvproxy.config

class IptvConnectionConfig protected constructor() {
    var url: String? = null
        private set
    var maxConnections: Int = 0
        private set
    var login: String? = null
        private set
    var password: String? = null
        private set

    class Builder {
        private val c = IptvConnectionConfig()

        fun build(): IptvConnectionConfig {
            return c
        }

        fun url(url: String?): Builder {
            c.url = url
            return this
        }

        fun maxConnections(maxConnections: Int): Builder {
            c.maxConnections = maxConnections
            return this
        }

        fun login(login: String?): Builder {
            c.login = login
            return this
        }

        fun password(password: String?): Builder {
            c.password = password
            return this
        }
    }
}
