package io.github.firstred.iptvproxy

import io.github.firstred.iptvproxy.utils.digest.Digest
import io.undertow.server.HttpServerExchange
import io.undertow.util.Headers
import io.undertow.util.HttpString
import io.undertow.util.SameThreadExecutor
import io.undertow.util.StatusCodes
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.net.HttpURLConnection
import java.net.URI
import java.net.URISyntaxException
import java.net.URLDecoder
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Flow.Publisher
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.function.Consumer
import kotlin.concurrent.Volatile
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

class IptvServerChannel(
    private val server: IptvServer, private val channelUrl: String, private val baseUrl: BaseUrl,
    val channelId: String, private val channelName: String, private val scheduler: ScheduledExecutorService
) {
    private val httpClient: HttpClient = server.httpClient

    @Volatile
    private var failedUntil: Long = 0

    private val defaultInfoTimeout: Long
    private val defaultCatchupTimeout: Long

    private val isHls: Boolean

    private class Stream(var path: String, var url: String, var header: String, var durationMillis: Long) {
        override fun toString(): String {
            return "[path: " + path + ", url: " + url + ", duration: " + (durationMillis / 1000f) + "s]"
        }
    }

    private class Streams {
        var streams: MutableList<Stream> = mutableListOf()
        var maxDuration: Long = 0
    }

    private fun interface StreamsConsumer {
        fun onInfo(streams: Streams?, statusCode: Int, retryNo: Int)
    }

    private class UserStreams(var infoTimeout: Long, var channelUrl: String) {
        var consumers: MutableList<StreamsConsumer> = ArrayList()
        var streamMap: Map<String, Stream> = HashMap()
        var maxDuration: Long = 0 // corresponds to current streamMap
        var isCatchup: Boolean = false

        val andClearConsumers: List<StreamsConsumer>
            get() {
                val c: List<StreamsConsumer> = consumers
                consumers = ArrayList()
                return c
            }
    }

    private val userStreams: MutableMap<String?, UserStreams> = ConcurrentHashMap()

    init {
        defaultInfoTimeout =
            (max(server.infoTotalTimeoutMs.toDouble(), server.infoTimeoutMs.toDouble()) + TimeUnit.SECONDS.toMillis(1)).roundToLong()
        defaultCatchupTimeout = (max(
            server.catchupTotalTimeoutMs.toDouble(),
            server.catchupTimeoutMs.toDouble()
        ) + TimeUnit.SECONDS.toMillis(1)).roundToLong()

        val uri = URI(channelUrl)
        isHls = uri.path.endsWith(".m3u8") || uri.path.endsWith(".m3u")
    }

    override fun toString(): String {
        return "[name: " + channelName + ", server: " + server.name + "]"
    }

    fun acquire(userId: String?): Boolean {
        if (System.currentTimeMillis() < failedUntil) {
            return false
        }

        if (server.acquire()) {
            LOG.info("[{}] channel acquired: {} / {}", userId, channelName, server.name)
            return true
        }

        return false
    }

    fun release(userId: String?) {
        LOG.info("[{}] channel released: {} / {}", userId, channelName, server.name)
        server.release()

        userStreams.remove(userId)
    }

    private fun createRequest(url: String, user: IptvUser?): HttpRequest {
        val builder = server.createRequest(url)

        // send user id to next iptv-proxy
        if (user != null && server.sendUser) {
            builder.header(IptvServer.PROXY_USER_HEADER, user.id)
        }

        return builder.build()
    }

    private fun calculateTimeout(duration: Long): Long {
        // usually we expect that player will try not to decrease buffer size
        // so we may expect that player will try to buffer more segments with durationMillis delay
        // kodi is downloading two or three buffers at same time
        // use 10 seconds for segment duration if unknown (5 or 7 seconds are usual values)
        return (if (duration == 0L) TimeUnit.SECONDS.toMillis(10) else duration) * 3 + TimeUnit.SECONDS.toMillis(1)
    }

    fun handle(exchange: HttpServerExchange, path: String, user: IptvUser, token: String): Boolean {
        if ("channel.m3u8" == path) {
            if (!isHls) {
                var url = exchange.requestURL.replace("channel.m3u8", "")
                val q = exchange.queryString
                if (q != null && !q.isBlank()) {
                    url += "?$q"
                }

                exchange.setStatusCode(StatusCodes.FOUND)
                exchange.responseHeaders.add(Headers.LOCATION, url)
                exchange.endExchange()
                return true
            }

            handleInfo(exchange, user, token)
            return true
        } else if ("" == path) {
            val rid = RequestCounter.next()
            LOG.info("{}[{}] channelUrl: {}", rid, user.id, channelUrl)

            runStream(rid, exchange, user, channelUrl, TimeUnit.SECONDS.toMillis(1))

            return true
        } else {
            // iptv user is synchronized (locked) at this place
            val us = userStreams[user.id]
            if (us == null) {
                LOG.warn("[{}] no streams set up: {}", user.id, exchange.requestPath)
                return false
            }

            val stream = us.streamMap[path]

            if (stream == null) {
                LOG.warn("[{}] stream not found: {}", user.id, exchange.requestPath)
                return false
            } else {
                val rid = RequestCounter.next()
                LOG.info("{}[{}] stream: {}", rid, user.id, stream)

                val timeout = calculateTimeout(us.maxDuration)
                user.setExpireTime(System.currentTimeMillis() + timeout)

                runStream(rid, exchange, user, stream.url, timeout)

                return true
            }
        }
    }

    private fun runStream(rid: String, exchange: HttpServerExchange, user: IptvUser, url: String, timeout: Long) {
        if (!server.proxyStream) {
            LOG.info("{}redirecting stream to direct url", rid)
            exchange.setStatusCode(StatusCodes.FOUND)
            exchange.responseHeaders.add(Headers.LOCATION, url)
            exchange.endExchange()
            return
        }

        // be sure we have time to start stream
        user.setExpireTime(System.currentTimeMillis() + server.streamStartTimeoutMs + 100)

        exchange.dispatch(SameThreadExecutor.INSTANCE, Runnable {
            val startNanos = System.nanoTime()
            // configure buffering according to undertow buffers settings for best performance
            httpClient.sendAsync(createRequest(url, user), HttpResponse.BodyHandlers.ofPublisher())
                .orTimeout(server.streamStartTimeoutMs, TimeUnit.MILLISECONDS)
                .whenComplete { resp: HttpResponse<Publisher<List<ByteBuffer>>>, err: Throwable? ->
                    if (HttpUtils.isOk(resp, err, exchange, rid, startNanos)) {
                        resp.headers().map()
                            .forEach { (name: String?, values: MutableList<String?>?) ->
                                if (HEADERS.contains(name?.lowercase(Locale.getDefault()))) {
                                    exchange.responseHeaders.addAll(HttpString(name!!), values)
                                }
                            }

                        exchange.responseHeaders.add(HttpUtils.ACCESS_CONTROL, "*")

                        val readTimeoutMs = server.streamReadTimeoutMs
                        resp.body().subscribe(
                            IptvStream(
                                exchange,
                                rid,
                                user,
                                max(timeout.toDouble(), readTimeoutMs.toDouble()).toLong(),
                                readTimeoutMs,
                                scheduler,
                                startNanos
                            )
                        )
                    }
                }
        })
    }

    private fun handleInfo(exchange: HttpServerExchange, user: IptvUser, token: String) {
        val us = createUserStreams(exchange, user)

        // we'll wait maximum one second for stream download start after loading info
        user.setExpireTime(System.currentTimeMillis() + us.infoTimeout)

        exchange.dispatch(SameThreadExecutor.INSTANCE, Runnable {
            val rid = RequestCounter.next()
            LOG.info("{}[{}] channel: {}, url: {}", rid, user.id, channelName, us.channelUrl)
            val startNanos = System.nanoTime()
            loadCachedInfo({ streams: Streams?, statusCode: Int, retryNo: Int ->
                if (streams == null) {
                    LOG.warn("{}[{}] error loading streams info: {}, retries: {}", rid, user.id, statusCode, retryNo)

                    exchange.setStatusCode(statusCode)
                    exchange.responseSender.send("error")
                } else {
                    val duration = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos)
                    if (duration > 500 || retryNo > 0) {
                        LOG.warn("{}[{}] channel success: {}ms, retries: {}", rid, user.id, duration, retryNo)
                    } else {
                        LOG.info("{}[{}] channel success: {}ms, retries: {}", rid, user.id, duration, retryNo)
                    }

                    val sb = StringBuilder()

                    streams.streams.forEach(Consumer { s: Stream ->
                        sb
                            .append(s.header)
                            .append(baseUrl.getBaseUrl(exchange))
                            .append('/').append(s.path).append("?t=").append(token).append("\n")
                    }
                    )

                    exchange.setStatusCode(HttpURLConnection.HTTP_OK)
                    exchange.responseHeaders
                        .add(Headers.CONTENT_TYPE, "application/x-mpegUrl")
                        .add(HttpUtils.ACCESS_CONTROL, "*")
                    exchange.responseSender.send(sb.toString())
                    exchange.endExchange()
                }
            }, user, us)
        })
    }

    private fun loadCachedInfo(consumer: StreamsConsumer, user: IptvUser, us: UserStreams) {
        var startReq: Boolean

        user.lock()
        try {
            startReq = us.consumers.size == 0
            us.consumers.add(consumer)
        } finally {
            user.unlock()
        }

        if (startReq) {
            loadInfo(
                RequestCounter.next(),
                0,
                System.currentTimeMillis() + (if (us.isCatchup) server.catchupTotalTimeoutMs else server.infoTotalTimeoutMs),
                user,
                us
            )
        }
    }

    private fun loadInfo(rid: String, retryNo: Int, expireTime: Long, user: IptvUser, us: UserStreams) {
        LOG.info("{}[{}] loading channel: {}, url: {}, retry: {}", rid, user.id, channelName, us.channelUrl, retryNo)

        var timeout = if (us.isCatchup) server.catchupTimeoutMs else server.infoTimeoutMs
        timeout = min(max(100.0, (expireTime - System.currentTimeMillis()).toDouble()), timeout.toDouble()).toLong()

        val startNanos = System.nanoTime()
        httpClient.sendAsync(createRequest(us.channelUrl, user), HttpResponse.BodyHandlers.ofString())
            .orTimeout(timeout, TimeUnit.MILLISECONDS)
            .whenComplete { resp: HttpResponse<String>?, err: Throwable? ->
                if (HttpUtils.isOk(resp, err, rid, startNanos)) {
                    val info = resp!!.body().split("\n".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()

                    val digest: Digest =
                        Digest.sha256()
                    var sb = StringBuilder()

                    val streamMap: MutableMap<String, Stream> =
                        HashMap()
                    val streams = Streams()

                    var durationMillis: Long = 0

                    for (l in info) {
                        var l = l
                        l = l.trim { it <= ' ' }

                        if (l.startsWith("#")) {
                            if (l.startsWith(TAG_EXTINF)) {
                                var v = l.substring(TAG_EXTINF.length)
                                val idx = v.indexOf(',')
                                if (idx >= 0) {
                                    v = v.substring(0, idx)
                                }

                                try {
                                    durationMillis = BigDecimal(v).multiply(BigDecimal(1000)).toLong()
                                    streams.maxDuration =
                                        max(streams.maxDuration.toDouble(), durationMillis.toDouble()).toLong()
                                } catch (e: NumberFormatException) {
                                    // do nothing
                                }
                            } else if (l.startsWith(TAG_TARGET_DURATION)) {
                                try {
                                    val targetDuration =
                                        BigDecimal(l.substring(TAG_TARGET_DURATION.length)).multiply(
                                            BigDecimal(1000)
                                        ).toLong()
                                    streams.maxDuration =
                                        max(streams.maxDuration.toDouble(), targetDuration.toDouble()).toLong()
                                } catch (e: NumberFormatException) {
                                    // do nothing
                                }
                            }

                            sb.append(l).append("\n")
                        } else {
                            // transform url
                            if (!l.startsWith("http://") && !l.startsWith("https://")) {
                                val idx = channelUrl.lastIndexOf('/')
                                if (idx >= 0) {
                                    val uri = resp.uri()
                                    l = uri.resolve(l).toString()
                                }
                            }

                            try {
                                val streamUri = URI(l)
                                // we need to redownload m3u8 if m3u8 is found insteadof .ts streams
                                if (streamUri.path.endsWith(".m3u8") || streamUri.path.endsWith(".m3u")) {
                                    val baseUri = URI(us.channelUrl)
                                    us.channelUrl = baseUri.resolve(streamUri).toString()
                                    loadInfo(rid, retryNo, expireTime, user, us)
                                    return@whenComplete
                                }
                            } catch (e: URISyntaxException) {
                                // probably we need to just skip this ?
                                LOG.trace("error parsing stream url", e)
                            }

                            val path = digest.digest(l) + ".ts"
                            val s = Stream(
                                path,
                                l,
                                sb.toString(),
                                durationMillis
                            )
                            streamMap[path] = s
                            streams.streams.add(s)

                            sb = StringBuilder()

                            durationMillis = 0
                        }
                    }

                    val cs: List<StreamsConsumer>

                    user.lock()
                    try {
                        us.streamMap = streamMap
                        us.maxDuration = streams.maxDuration

                        us.infoTimeout = calculateTimeout(us.maxDuration)
                        user.setExpireTime(System.currentTimeMillis() + us.infoTimeout)

                        cs = us.andClearConsumers
                    } finally {
                        user.unlock()
                    }

                    cs.forEach(Consumer { c: StreamsConsumer ->
                        c.onInfo(
                            streams,
                            -1,
                            retryNo
                        )
                    })
                } else {
                    if (System.currentTimeMillis() < expireTime) {
                        LOG.info("{}[{}] will retry", rid, user.id)

                        scheduler.schedule(
                            { loadInfo(rid, retryNo + 1, expireTime, user, us) },
                            if (us.isCatchup) server.catchupRetryDelayMs else server.infoRetryDelayMs,
                            TimeUnit.MILLISECONDS
                        )
                    } else {
                        if (server.channelFailedMs > 0) {
                            user.lock()
                            try {
                                LOG.warn("{}[{}] channel failed", rid, user.id)
                                failedUntil = System.currentTimeMillis() + server.channelFailedMs
                                user.releaseChannel()
                            } finally {
                                user.unlock()
                            }
                        } else {
                            LOG.warn("{}[{}] streams failed", rid, user.id)
                        }

                        val statusCode = resp?.statusCode() ?: HttpURLConnection.HTTP_INTERNAL_ERROR
                        us.andClearConsumers.forEach(Consumer { c: StreamsConsumer ->
                            c.onInfo(
                                null,
                                statusCode,
                                retryNo
                            )
                        })
                    }
                }
            }
    }

    private fun createUserStreams(exchange: HttpServerExchange, user: IptvUser): UserStreams {
        val url = createChannelUrl(exchange)

        // user is locked here
        var us = userStreams[user.id]
        if (us == null || us.channelUrl != url) {
            val isCatchup = exchange.queryParameters.containsKey("utc") ||
                    exchange.queryParameters.containsKey("lutc")
            us = UserStreams(if (isCatchup) defaultCatchupTimeout else defaultInfoTimeout, url)
            us.isCatchup = isCatchup
            userStreams[user.id] = us
        }

        return us
    }

    private fun createChannelUrl(exchange: HttpServerExchange): String {
        val qp: MutableMap<String, String?> = TreeMap()
        exchange.queryParameters.forEach { (k: String?, v: Deque<String?>?) ->
            if (k == null || v == null) return@forEach

            // skip our token tag
            if ("t" != k) {
                if (v.size > 0) {
                    qp[k] = v.first
                }
            }
        }

        if (qp.isEmpty()) {
            return channelUrl
        }

        val uri: URI

        try {
            uri = URI(channelUrl)
        } catch (se: URISyntaxException) {
            throw RuntimeException(se)
        }

        if (uri.rawQuery != null && !uri.rawQuery.isBlank()) {
            for (pair in uri.rawQuery.split("&".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()) {
                val idx = pair.indexOf('=')
                val key = URLDecoder.decode(if (idx >= 0) pair.substring(0, idx) else pair, StandardCharsets.UTF_8)
                val value = if (idx < 0) null else URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8)
                qp.putIfAbsent(key, value)
            }
        }

        val q = StringBuilder()
        qp.forEach { (k: String?, v: String?) ->
            if (!q.isEmpty()) {
                q.append('&')
            }
            q.append(URLEncoder.encode(k, StandardCharsets.UTF_8))
            if (v != null) {
                q.append('=').append(URLEncoder.encode(v, StandardCharsets.UTF_8))
            }
        }

        try {
            return URI(
                uri.scheme, uri.userInfo, uri.host,
                uri.port, uri.path, q.toString(), uri.fragment
            ).toString()
        } catch (e: URISyntaxException) {
            throw RuntimeException(e)
        }
    }

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(IptvServerChannel::class.java)

        private const val TAG_EXTINF = "#EXTINF:"
        private const val TAG_TARGET_DURATION = "#EXT-X-TARGETDURATION:"

        private val HEADERS: Set<String> = HashSet(
            mutableListOf(
                "content-type",
                "content-length",
                "connection",
                "date",  //"access-control-allow-origin",
                "access-control-allow-headers",
                "access-control-allow-methods",
                "access-control-expose-headers",
                "x-memory",
                "x-route-time",
                "x-run-time"
            )
        )
    }
}
