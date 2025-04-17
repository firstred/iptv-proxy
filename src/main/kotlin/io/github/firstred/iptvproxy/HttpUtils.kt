package io.github.firstred.iptvproxy

import io.undertow.server.HttpServerExchange
import io.undertow.util.HttpString
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.HttpURLConnection
import java.net.http.HttpResponse
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

object HttpUtils {
    private val LOG: Logger = LoggerFactory.getLogger(HttpUtils::class.java)

    var ACCESS_CONTROL: HttpString = HttpString("Access-Control-Allow-Origin")

    fun isOk(resp: HttpResponse<*>?, err: Throwable?, rid: String, startNanos: Long): Boolean {
        return isOk(resp, err, null, rid, startNanos)
    }

    fun isOk(
        resp: HttpResponse<*>?,
        err: Throwable?,
        exchange: HttpServerExchange?,
        rid: String,
        startNanos: Long
    ): Boolean {
        if (resp == null) {
            val errMsg =
                if (err == null || err is TimeoutException) "timeout" else ((if (err.message == null) err.toString() else err.message)!!)
            LOG.warn(rid + "io error: {}", errMsg)
            if (exchange != null) {
                exchange.setStatusCode(HttpURLConnection.HTTP_INTERNAL_ERROR)
                exchange.responseSender.send("error")
            }
            return false
        } else if (resp.statusCode() != HttpURLConnection.HTTP_OK) {
            LOG.warn(rid + "bad status code: {} for url: {}", resp.statusCode(), resp.uri())
            if (exchange != null) {
                exchange.setStatusCode(resp.statusCode())
                exchange.responseSender.send("error")
            }
            return false
        } else {
            LOG.debug("{}ok ({}ms)", rid, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos))
        }

        return true
    }
}
