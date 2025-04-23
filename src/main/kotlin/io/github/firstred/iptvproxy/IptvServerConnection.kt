package io.github.firstred.iptvproxy

import io.github.firstred.iptvproxy.dtos.config.IptvFlatServerConfig
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.sync.Semaphore
import org.koin.core.component.KoinComponent
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.concurrent.Volatile

class IptvServerConnection(
    private val config: IptvFlatServerConfig,
) : KoinComponent {
    private val semaphore: Semaphore = Semaphore(config.account.maxUserConnections)

    private val httpClient: HttpClient

    init{
        httpClient = HttpClient(OkHttp)
    }

    @Volatile
    private var failedUntil: Long = 0

//    private val isHls: Boolean

    private fun HttpResponse.isOk(err: Throwable?): Boolean {
        if (err != null) {
            LOG.warn("error loading stream {}", err.message)
            return false
        }

        if (status.value != HttpStatusCode.OK.value) {
            LOG.warn("error loading stream: {}", status)
            return false
        }

        return true
    }

    private class Stream(var path: String, var url: String, var prefix: String, var durationMillis: Long) {
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

    init {
//        val uri = URI(channelUrl)
//        isHls = uri.path.endsWith(".m3u8") || uri.path.endsWith(".m3u")
    }

//    override fun toString(): String {
//        return "[name: " + channelName + ", server: " + server.name + "]"
//    }
//
//    private fun createRequest(url: String, user: UserSemaphores?): HttpRequest {
//        val builder = server.createRequest(url)
//
//        // send user id to next iptv-proxy
//        if (user != null && server.sendUser) {
//            builder.header(IptvServer.PROXY_USER_HEADER, username)
//        }
//
//        return builder.build()
//    }
//
//    suspend fun handle(call: RoutingCall, path: String, user: UserSemaphores, token: String): Boolean {
//        if ("channel.m3u8" == path) {
//            if (!isHls) {
//                var url = call.request.uri.replace("channel.m3u8", "")
//                val q = call.request.queryString()
//                if (q.isNotBlank()) {
//                    url += "?$q"
//                }
//
//                call.response.status(HttpStatusCode.Found)
//                call.response.headers.append(HttpHeaders.Location, url)
//                return true
//            }
//
//            generateChannelPlaylist(username, path)
//            return true
//        } else if ("" == path) {
//            LOG.info("[{}] channelUrl: {}", username, channelUrl)
//
//            runStream(call, user, channelUrl)
//
//            return true
//        } else {
//            // iptv user is synchronized (locked) at this place
//            val us = userStreams[username]
//            if (us == null) {
//                LOG.warn("[{}] no streams set up: {}", username, call.request.path())
//                return false
//            }
//
//            val stream = us.streamMap[path]
//
//            if (stream == null) {
//                LOG.warn("[{}] stream not found: {}", username, call.request.path())
//                return false
//            } else {
//                LOG.info("{}[{}] stream: {}", username, stream)
//
//                runStream(call, user, stream.url)
//
//                return true
//            }
//        }
//    }
//
//    private fun runStream(call: RoutingCall, user: UserSemaphores, url: String) {
//        if (!server.proxyStream) {
//            LOG.info("Redirecting stream to direct url")
//            call.response.status(HttpStatusCode.Found)
//            call.response.headers.append(HttpHeaders.Location, url)
//            return
//        }
//
//        // be sure we have time to start stream
////        user.setExpireTime(System.currentTimeMillis() + server.streamStartTimeoutMs + 100)
//
//        val startNanos = System.nanoTime()
//
//        val request = createRequest(url, user)
//        runBlocking {
//            var response: HttpResponse? = null
//            var err: Throwable? = null
//
//            try {
//                response = httpClient.request(request.uri().toString()) {
//                    method = HttpMethod.parse(request.method())
//                }
//            } catch (e: Throwable) {
//                err = e
//            }
//
//            if (response?.isOk(err) ?: false) {
//                response!!.headers.entries().forEach { (name: String, values: List<String>) ->
//                    if (PROXIES_HEADERS.contains(name.lowercase(Locale.getDefault()))) {
//                        values.forEach { value: String ->
//                            call.response.headers.append(name, value)
//                        }
//                    }
//                }
//
//                call.response.headers.append(HttpHeaders.AccessControlAllowOrigin, "*")
//
//                // Convert response to flow.subscribe
//                val responseChannel = response.bodyAsChannel()
//
//                while (!responseChannel.exhausted()) {
//                    val chunk = responseChannel.readBuffer()
//                    call.respondBytes(chunk.readByteArray())
//                }
//            }
////            IptvStream(
////                call,
////                rid,
////                user,
////                max(timeout.toDouble(), server.infoTimeoutMs.toDouble()).toLong(),
////                server.infoTimeoutMs,
////                scheduler,
////                startNanos,
////            )
//        }
//
////        httpClient.sendAsync(createRequest(url, user), HttpResponse.BodyHandlers.ofPublisher())
////            .orTimeout(server.streamStartTimeoutMs, TimeUnit.MILLISECONDS)
////            .whenComplete { resp: HttpResponse<Publisher<List<ByteBuffer>>>, err: Throwable? ->
////                if (HttpUtils.isOk(resp, err, exchange, rid, startNanos)) {
////                    resp.headers().map()
////                        .forEach { (name: String?, values: MutableList<String?>?) ->
////                            if (HEADERS.contains(name?.lowercase(Locale.getDefault()))) {
////                                exchange.responseHeaders.addAll(HttpString(name!!), values)
////                            }
////                        }
////
////                    exchange.responseHeaders.add(HttpUtils.ACCESS_CONTROL, "*")
////
////                    val readTimeoutMs = server.streamReadTimeoutMs
////                    resp.body().subscribe(
////                        IptvStream(
////                            exchange,
////                            rid,
////                            user,
////                            max(timeout.toDouble(), readTimeoutMs.toDouble()).toLong(),
////                            readTimeoutMs,
////                            scheduler,
////                            startNanos,
////                        )
////                    )
////                }
////            }
//    }



//    private fun createUserStreams(username: String, channelUrl: String): UserStreams {
////        val url = createChannelUrl(call)
//
//        // user is locked here
//        var userStream = userStreams[username]
//        if (userStream == null || userStream.channelUrl != channelUrl) {
////            val isCatchup = call.request.queryParameters.contains("utc") ||
////                    call.request.queryParameters.contains("lutc")
//            userStream = UserStreams(username, channelUrl)
////            userStream.isCatchup = isCatchup
//            userStreams[username] = userStream
//        }
//
//        return userStream
//    }


    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(IptvServerConnection::class.java)


    }
}
