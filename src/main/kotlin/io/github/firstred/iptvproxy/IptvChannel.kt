package io.github.firstred.iptvproxy

import io.github.firstred.iptvproxy.utils.generateUserToken
import io.ktor.server.routing.*
import io.ktor.utils.io.core.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.InputStream
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.text.toByteArray

class IptvChannel(
    val reference: String,
    val name: String,
    val logo: String,
    val xmltvId: String,
    val catchupDays: Int,
    val server: IptvServer,
    groups: Collection<String>,
) {
    val groups: Set<String> = Collections.unmodifiableSet(TreeSet(groups))

    suspend fun retrieveChannelPlaylist(): InputStream {
        TODO()
//        LOG.info("[{}] channel: {}",  username, id)
//        val startNanos = System.nanoTime()
//
//        val sb = StringBuilder()
//
//        loadCachedInfo({ streams: Streams?, statusCode: Int, retryNo: Int ->
//            if (streams == null) {
//                IptvServerConnection.LOG.warn("[{}] error loading streams info: {}, retries: {}", username, statusCode, retryNo)
//                throw RuntimeException("error loading streams info")
//            } else {
//                val duration = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos)
//                if (duration > 500 || retryNo > 0) {
//                    IptvServerConnection.LOG.warn("[{}] channel success: {}ms, retries: {}", username, duration, retryNo)
//                } else {
//                    IptvServerConnection.LOG.info("[{}] channel success: {}ms, retries: {}", username, duration, retryNo)
//                }
//
//                streams.streams.forEach { stream: Stream ->
//                    buffer.write(
//                        "${stream.prefix}${config.baseUrl}/${username.generateUserToken()}/${stream.path}\n".toByteArray()
//                    )
//                }
//            }
//        }, userStream)
//
//        return sb.toString()
    }

    private suspend fun loadInfo(retryNo: Int, expireTime: Long, username: String) {
        TODO()
//        IptvServerConnection.LOG.info("[{}] loading channel: {}, url: {}, retry: {}", username, channelName, userStreams.channelUrl, retryNo)
//
//        val request = createRequest(userStreams.channelUrl, username)
//
//        var response: HttpResponse? = null
//        var err: Throwable? = null
//
//        try {
//            response = httpClient.request(request.uri().toString())
//        } catch (e: Throwable) {
//            err = e
//        }
//
//        if (response?.isOk(err) ?: false) {
//            val info = response!!.bodyAsText().split("\n".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
//
//            var sb = StringBuilder()
//
//            val streamMap: MutableMap<String, Stream> = mutableMapOf()
//            val streams = Streams()
//
//            var durationMillis: Long = 0
//
//            for (infoLine in info) {
//                @Suppress("NAME_SHADOWING") var infoLine = infoLine
//                infoLine = infoLine.trim(' ')
//
//                if (infoLine.startsWith("#")) {
//                    if (infoLine.startsWith(TAG_EXTINF)) {
//                        var v = infoLine.substring(TAG_EXTINF.length)
//                        val idx = v.indexOf(',')
//                        if (idx >= 0) {
//                            v = v.substring(0, idx)
//                        }
//
//                        try {
//                            durationMillis = BigDecimal(v).multiply(BigDecimal(1000)).toLong()
//                            streams.maxDuration = max(streams.maxDuration.toDouble(), durationMillis.toDouble()).toLong()
//                        } catch (e: NumberFormatException) {
//                            // do nothing
//                        }
//                    } else if (infoLine.startsWith(TAG_TARGET_DURATION)) {
//                        try {
//                            val targetDuration =
//                                BigDecimal(infoLine.substring(TAG_TARGET_DURATION.length)).multiply(
//                                    BigDecimal(1000)
//                                ).toLong()
//                            streams.maxDuration = max(streams.maxDuration.toDouble(), targetDuration.toDouble()).toLong()
//                        } catch (e: NumberFormatException) {
//                            // do nothing
//                        }
//                    }
//
//                    sb.append(infoLine).append("\n")
//                } else {
//                    // transform url
//                    if (!infoLine.startsWith("http://") && !infoLine.startsWith("https://")) {
//                        val idx = channelUrl.lastIndexOf('/')
//                        if (idx >= 0) {
//                            val uri = response.request.url.toURI()
//                            infoLine = uri.resolve(infoLine).toString()
//                        }
//                    }
//
//                    try {
//                        val streamUri = URI(infoLine)
//                        // we need to redownload m3u8 if m3u8 is found insteadof .ts streams
//                        if (streamUri.path.endsWith(".m3u8") || streamUri.path.endsWith(".m3u")) {
//                            val baseUri = URI(userStreams.channelUrl)
//                            userStreams.channelUrl = baseUri.resolve(streamUri).toString()
//                            loadInfo(retryNo, expireTime, username, userStreams)
//                            return
//                        }
//                    } catch (e: URISyntaxException) {
//                        // probably we need to just skip this ?
//                        IptvServerConnection.LOG.trace("error parsing stream url", e)
//                    }
//
//                    val path = infoLine.hash() + ".ts"
//                    val s = Stream(
//                        path,
//                        infoLine,
//                        sb.toString(),
//                        durationMillis
//                    )
//                    streamMap[path] = s
//                    streams.streams.add(s)
//
//                    sb = StringBuilder()
//
//                    durationMillis = 0
//                }
//            }
//        } else {
//            if (System.currentTimeMillis() < expireTime) {
//                LOG.info("[{}] will retry", username)
//            } else {
////                if (server.channelFailedMs > 0) {
////                    user.lock()
////                    try {
////                        LOG.warn("{}[{}] channel failed", rid, user.id)
////                        failedUntil = System.currentTimeMillis() + server.channelFailedMs
////                        user.releaseChannel()
////                    } finally {
////                        user.unlock()
////                    }
////                } else {
////                    LOG.warn("{}[{}] streams failed", rid, user.id)
////                }
//
//                val statusCode = response!!.status.value
//                userStreams.andClearConsumers.forEach { c: StreamsConsumer ->
//                    c.onInfo(
//                        null,
//                        statusCode,
//                        retryNo
//                    )
//                }
//            }
//        }
    }

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(IptvChannel::class.java)
    }
}
