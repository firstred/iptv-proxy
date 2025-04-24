package io.github.firstred.iptvproxy.entities

import io.github.firstred.iptvproxy.utils.M3U_TAG_EXTINF
import io.github.firstred.iptvproxy.utils.M3U_TAG_TARGET_DURATION
import io.github.firstred.iptvproxy.utils.aesEncryptToHexString
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
    val reference: String,
    val name: String,
    val logo: String,
    val xmltvId: String,
    val url: URI,
    val catchupDays: Int,
    val server: IptvServer,
    groups: Collection<String>,
) {
    val groups: Set<String> = Collections.unmodifiableSet(TreeSet(groups))

    suspend fun getPlaylist(
        user: IptvUser,
        baseUrl: URI,
        additionalHeaders: Headers = headersOf(),
        additionalParams: Parameters = parametersOf(),
    ): String {
        val outputStream = ByteArrayOutputStream()
        getPlaylist(outputStream, user, baseUrl, additionalHeaders, additionalParams)

        return outputStream.toString(UTF_8.toString())
    }

    suspend fun getPlaylist(
        outputStream: OutputStream,
        user: IptvUser,
        baseUrl: URI,
        additionalHeaders: Headers = headersOf(),
        additionalParams: Parameters = parametersOf(),
    ) {
        val outputWriter = outputStream.bufferedWriter(UTF_8)

        server.withConnection { connection ->
            LOG.info("[{}] loading channel: {}, url: {}", user.username, name, reference)

            var response = connection.httpClient.get(url.toString())
            var responseURI = url

            var redirects = 0

            // Check if response is a redirect
            while (null != response.headers["Location"] && redirects < maxRedirects) {
                val location = response.headers["Location"] ?: break

                // Follow redirect
                response = connection.httpClient.get(location)
                response.body<String>()
                try {
                    responseURI = responseURI.resolve(URI.create(location))
                } catch (_: URISyntaxException) {
                }

                redirects++
            }

            var maxDurationMillis = 0L
            var currentDurationMillis = 0L

            for (infoLine in response.body<String>().lines()) {
                @Suppress("NAME_SHADOWING") var infoLine = infoLine
                infoLine = infoLine.trim(' ')

                if (infoLine.startsWith("#")) {
                    // This is a metadata line
                    if (infoLine.startsWith(M3U_TAG_EXTINF)) {
                        var v = infoLine.substring(M3U_TAG_EXTINF.length)
                        val idx = v.indexOf(',')
                        if (idx >= 0) v = v.substring(0, idx)

                        currentDurationMillis = BigDecimal(v).multiply(BigDecimal(1000)).toLong()
                        maxDurationMillis = max(maxDurationMillis.toDouble(), currentDurationMillis.toDouble()).toLong()
                    } else if (infoLine.startsWith(M3U_TAG_TARGET_DURATION)) {
                        val targetDuration =
                            BigDecimal(infoLine.substring(M3U_TAG_TARGET_DURATION.length)).multiply(
                                BigDecimal(1000)
                            ).toLong()
                        maxDurationMillis = max(maxDurationMillis.toDouble(), targetDuration.toDouble()).toLong()
                    }
                } else {
                    // This is a stream URL

                    if (infoLine.isNotBlank()) {
                        if (!infoLine.startsWith("http://") && !infoLine.startsWith("https://")) {
                            infoLine = responseURI.resolve(infoLine).toString()
                        }
                        try {
                            val infoLineUri = URI.create(infoLine)

                            infoLine = "${baseUrl}hls/${user.toEncryptedAccountHexString()}/${reference}/${infoLineUri.aesEncryptToHexString()}/live.ts"
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
