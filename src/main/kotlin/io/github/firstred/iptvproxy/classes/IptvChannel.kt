package io.github.firstred.iptvproxy.classes

import io.github.firstred.iptvproxy.enums.IptvChannelType
import io.github.firstred.iptvproxy.parsers.M3uParser
import io.github.firstred.iptvproxy.utils.addHeadersFromPlaylistProps
import io.github.firstred.iptvproxy.utils.addProxyAuthorizationHeaderIfNecessary
import io.github.firstred.iptvproxy.utils.aesEncryptToHexString
import io.github.firstred.iptvproxy.utils.appendQueryParameters
import io.github.firstred.iptvproxy.utils.forwardProxyUser
import io.github.firstred.iptvproxy.utils.maxRedirects
import io.github.firstred.iptvproxy.utils.sendBasicAuth
import io.github.firstred.iptvproxy.utils.sendUserAgent
import io.github.firstred.iptvproxy.utils.toEncodedJavaURI
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.net.URI
import java.net.URISyntaxException
import java.util.*
import kotlin.text.Charsets.UTF_8

class IptvChannel(
    val id: UInt? = null,
    val externalPosition: UInt? = null,
    val externalStreamId: UInt? = null,
    val name: String,
    val logo: String?,
    val epgId: String?,
    val url: URI,
    val catchupDays: Int,
    val server: IptvServer,
    val groups: List<String>,
    val type: IptvChannelType,
    val m3uProps: Map<String, String>,
    val vlcOpts: Map<String, String>,
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

        server.withConnection(server.config.timeouts.totalMilliseconds) { connection, _ ->
            LOG.trace("[{}] loading channel: {}, url: {}", user.username, name, url)

            lateinit var response: HttpResponse
            response = connection.httpClient.get(
                url.appendQueryParameters(additionalQueryParameters).toString(),
            ) {
                headers {
                    additionalHeaders.forEach { key, values -> values.forEach { value -> append(key, value) } }
                    forwardProxyUser(connection.config)
                    sendUserAgent(connection.config)
                    if (null != connection.config.account) sendBasicAuth(connection.config.account)
                    addHeadersFromPlaylistProps(m3uProps, vlcOpts)
                    addProxyAuthorizationHeaderIfNecessary()
                }
            }
            headersCallback?.invoke(response.headers)

            var responseURI = url

            var redirects = 0

            // Check if response is a redirect
            while (null != response.headers["Location"] && redirects < maxRedirects) {
                val location = response.headers["Location"] ?: break

                // Follow redirects
                response = connection.httpClient.get(location) {
                    headers {
                        additionalHeaders.forEach { key, values -> values.forEach { value -> append(key, value) } }
                        forwardProxyUser(connection.config)
                        sendUserAgent(connection.config)
                        if (null != connection.config.account) sendBasicAuth(connection.config.account)
                        addHeadersFromPlaylistProps(m3uProps, vlcOpts)
                        addProxyAuthorizationHeaderIfNecessary()
                    }
                }
                response.body<String>()
                try {
                    responseURI = responseURI.resolve(location)
                } catch (_: URISyntaxException) {
                }

                redirects++
            }

            for (infoLine in response.body<String>().lines()) {
                var infoLine = infoLine

                when {
                    // Only rewrite direct media URLs this time, no icons etc.
                    !infoLine.trim(' ').startsWith("#") && infoLine.trim(' ').isNotBlank() -> {
                        var newInfoLine = infoLine.trim(' ')

                        try {
                            // This is a stream URL
                            if (!newInfoLine.startsWith("http://") && !newInfoLine.startsWith("https://")) {
                                newInfoLine = responseURI.resolve(newInfoLine).toString()
                            }

                            val remoteUrl = Url(newInfoLine).toEncodedJavaURI()
                            val fileName = remoteUrl.path.substringAfterLast('/', "").substringBeforeLast('.')
                            val extension = remoteUrl.path.substringAfterLast('.', "")

                            infoLine = "${baseUrl}hls/${user.toEncryptedAccountHexString()}/${remoteUrl.aesEncryptToHexString()}/$id/$fileName.$extension"
                        } catch (e: URISyntaxException) {
                            LOG.warn(
                                "[{}] Error while parsing stream URL: {}, error: {}",
                                user.username,
                                infoLine,
                                e.message,
                            )
                        }
                    }

                    arrayOf("#EXT-MEDIA-INFO", "#EXT-X-MEDIA", "#EXT-X-SESSION-DATA", "#EXT-X-STREAM-INF", "#EXT-X-I-FRAME-STREAM-INF").contains(infoLine.trim(' ').substringBefore(':').uppercase()) -> {
                        val props = M3uParser.parseProps(infoLine.substringAfter(':'))

                        // Rewrite the URI prop if it exists
                        props["URI"]?.let {
                            try {
                                // Remove quotes from the URI if they exist
                                val unquotedUrl = it.removeSurrounding("\"")
                                val remoteUrl = if (!unquotedUrl.startsWith("http://") && !unquotedUrl.startsWith("https://")) {
                                    Url(responseURI.resolve(unquotedUrl)).toEncodedJavaURI()
                                } else {
                                    Url(unquotedUrl).toEncodedJavaURI()
                                }
                                val fileName = remoteUrl.path.substringAfterLast('/', "").substringBeforeLast('.')
                                val extension = remoteUrl.path.substringAfterLast('.', "")

                                props["URI"] = "\"${baseUrl}hls/${user.toEncryptedAccountHexString()}/${remoteUrl.aesEncryptToHexString()}/$id/$fileName.$extension\""
                            } catch (e: URISyntaxException) {
                                LOG.warn(
                                    "[{}] Error while parsing media info URI: {}, error: {}",
                                    user.username,
                                    it,
                                    e.message,
                                )
                            }
                        }

                        val tag = infoLine.substringBefore(':').uppercase()
                        infoLine = "$tag:${props.entries.joinToString(",") { "${it.key}=${it.value}" }}"
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
