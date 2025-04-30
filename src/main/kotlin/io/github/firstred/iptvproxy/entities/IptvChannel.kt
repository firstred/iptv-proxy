package io.github.firstred.iptvproxy.entities

import io.github.firstred.iptvproxy.enums.IptvChannelType
import io.github.firstred.iptvproxy.utils.EXT_X_MEDIA_SEQUENCE
import io.github.firstred.iptvproxy.utils.M3U_TAG_EXTINF
import io.github.firstred.iptvproxy.utils.M3U_TAG_TARGET_DURATION
import io.github.firstred.iptvproxy.utils.aesEncryptToHexString
import io.github.firstred.iptvproxy.utils.appendQueryParameters
import io.github.firstred.iptvproxy.utils.maxRedirects
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.math.BigDecimal
import java.net.URI
import java.net.URISyntaxException
import java.util.*
import kotlin.math.max
import kotlin.text.Charsets.UTF_8

class IptvChannel(
    val id: String? = null,
    val externalStreamId: String? = null,
    val name: String,
    val logo: String?,
    val epgId: String?,
    val url: URI,
    val catchupDays: Int,
    val server: IptvServer,
    val groups: List<String>,
    val type: IptvChannelType,
) {
    suspend fun getPlaylist(
        user: IptvUser,
        baseUrl: URI,
        additionalHeaders: Headers = headersOf(),
        additionalQueryParameters: Parameters = parametersOf(),
        headersCallback: ((Headers) -> Unit)? = null,
    ): String {
        val outputStream = ByteArrayOutputStream()
        getPlaylist(outputStream, user, baseUrl, additionalHeaders, additionalQueryParameters, headersCallback)

        return outputStream.toString(UTF_8.toString())
    }

    suspend fun getPlaylist(
        outputStream: OutputStream,
        user: IptvUser,
        baseUrl: URI,
        additionalHeaders: Headers = headersOf(),
        additionalQueryParameters: Parameters = parametersOf(),
        headersCallback: ((Headers) -> Unit)? = null,
    ) {
        val outputWriter = outputStream.bufferedWriter(UTF_8)

        server.withConnection { connection ->
            LOG.info("[{}] loading channel: {}, url: {}", user.username, name)

            var response = connection.httpClient.get(
                url.appendQueryParameters(additionalQueryParameters).toString(),
            ) {
                headers {
                    additionalHeaders.forEach { key, values -> values.forEach { value -> append(key, value) } }
                }
            }
            headersCallback?.invoke(response.headers)

            var responseURI = url

            var redirects = 0

            // Check if response is a redirect
            while (null != response.headers["Location"] && redirects < maxRedirects) {
                val location = response.headers["Location"] ?: break

                // Follow redirect
                response = connection.httpClient.get(location)
                response.body<String>()
                try {
                    responseURI = responseURI.resolve(URI(location))
                } catch (_: URISyntaxException) {
                }

                redirects++
            }

            var mediaSequenceNumber = 1
            var maxDurationMillis = 0L
            var currentDurationMillis: Long

            for (infoLine in response.body<String>().lines()) {
                @Suppress("NAME_SHADOWING") var infoLine = infoLine
                infoLine = infoLine.trim(' ')

                // This is a metadata line
                when {
                    infoLine.startsWith(M3U_TAG_EXTINF) -> {
                        var v = infoLine.substring(M3U_TAG_EXTINF.length)
                        val idx = v.indexOf(',')
                        if (idx >= 0) v = v.substring(0, idx)

                        currentDurationMillis = BigDecimal(v).multiply(BigDecimal(1000)).toLong()
                        maxDurationMillis = max(maxDurationMillis.toDouble(), currentDurationMillis.toDouble()).toLong()
                    }

                    infoLine.startsWith(M3U_TAG_TARGET_DURATION) -> {
                        val targetDuration =
                            BigDecimal(infoLine.substring(M3U_TAG_TARGET_DURATION.length)).multiply(
                                BigDecimal(1000)
                            ).toLong()
                        maxDurationMillis = max(maxDurationMillis.toDouble(), targetDuration.toDouble()).toLong()
                    }

                    infoLine.startsWith(EXT_X_MEDIA_SEQUENCE) -> {
                        mediaSequenceNumber = infoLine.substring(EXT_X_MEDIA_SEQUENCE.length).toInt()
                    }

                    !infoLine.startsWith("#") && infoLine.isNotBlank() -> {
                        // This is a stream URL
                        if (!infoLine.startsWith("http://") && !infoLine.startsWith("https://")) {
                            infoLine = responseURI.resolve(infoLine).toString()
                        }
                        try {
                            val infoLineUri = URI(infoLine)

                            infoLine =
                                "${baseUrl}hls/${user.toEncryptedAccountHexString()}/${id}/${infoLineUri.aesEncryptToHexString()}/live_${++mediaSequenceNumber}.ts"
                        } catch (_: URISyntaxException) {
                        }
                    }
                }

                outputWriter.write("$infoLine\n")
                outputWriter.flush()
            }
        }
    }

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(IptvChannel::class.java)
    }
}
