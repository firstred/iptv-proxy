package io.github.firstred.iptvproxy

import io.undertow.server.HttpServerExchange

class BaseUrl private constructor(
    private val baseUrl: String?,
    private val forwardedPass: String?,
    private val path: String,
) {
    constructor(baseUrl: String, forwardedPass: String?) : this(baseUrl, forwardedPass, "")

    fun forPath(path: String): BaseUrl {
        return BaseUrl(baseUrl, forwardedPass, this.path + path)
    }

    fun getBaseUrl(exchange: HttpServerExchange): String {
        return getBaseUrlWithoutPath(exchange) + path
    }

    private fun getBaseUrlWithoutPath(exchange: HttpServerExchange): String {
        if (forwardedPass != null) {
            val fwd = exchange.requestHeaders.getFirst("Forwarded")
            if (fwd != null) {
                var pass: String? = null
                var baseUrl: String? = null
                for (pair in fwd.split(";".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()) {
                    val idx = pair.indexOf('=')
                    if (idx >= 0) {
                        val key = pair.substring(0, idx)
                        val value = pair.substring(idx + 1)
                        if ("pass" == key) {
                            pass = value
                        } else if ("baseUrl" == key) {
                            baseUrl = value
                        }
                    }
                }

                if (baseUrl != null && forwardedPass == pass) {
                    return baseUrl
                }
            }
        }

        if (baseUrl != null) {
            return baseUrl
        }

        return exchange.requestScheme + "://" + exchange.hostAndPort
    }
}
