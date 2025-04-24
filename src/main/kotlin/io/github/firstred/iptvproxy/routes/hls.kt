package io.github.firstred.iptvproxy.routes

import io.github.firstred.iptvproxy.config
import io.github.firstred.iptvproxy.di.modules.IptvChannelsByReference
import io.github.firstred.iptvproxy.plugins.findUserFromRoutingContext
import io.github.firstred.iptvproxy.plugins.isNotMainEndpoint
import io.github.firstred.iptvproxy.utils.aesDecryptFromHexString
import io.ktor.client.request.request
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.cio.use
import io.ktor.utils.io.copyAndClose
import org.koin.ktor.ext.inject
import java.io.File
import kotlin.concurrent.timer

fun Route.hls() {
    val channelsByReference: IptvChannelsByReference by inject()

    route(Regex("""/hls/(?<encryptedaccount>[0-9a-fA-F]+)/(?<channelid>[^/]+)/(?<encryptedremoteurl>[0-9a-fA-F]+)/[^.]*\.ts""")) {
        get {
            if (isNotMainEndpoint()) return@get
            findUserFromRoutingContext()

            val channelId = call.parameters["channelid"] ?: ""
            val remoteUrl = (call.parameters["encryptedremoteurl"] ?: "").aesDecryptFromHexString()

            channelsByReference[channelId]!!.server.withConnection { connection ->
                connection.httpClient.request {
                    url(remoteUrl)
                    method = HttpMethod.Get
                }.let { response ->
                    call.response.headers.apply {
                        response.contentLength()?.let { append(HttpHeaders.ContentLength, it.toString()) }
                        response.contentType()?.let { append(HttpHeaders.ContentType, it.toString()) }
                    }

                    call.respondBytesWriter { use {
                        response.bodyAsChannel().copyAndClose(this)
                    } }
                }
            }
        }
    }
}

fun Route.hlsOnlyNoticeStream() {
    // Unix timestamp in seconds
    var sequenceNumber = System.currentTimeMillis() / 10_000L
    timer(period = 10_000L) { sequenceNumber++ }

    get {
        if (isNotMainEndpoint()) return@get

        call.response.headers.apply {
            append(HttpHeaders.ContentType, "audio/mpegurl")
            append(HttpHeaders.ContentDisposition, "attachment; filename=playlist.m3u8")
        }

        val basePath = call.request.path()

        call.respondText(
            """
#EXTM3U
#EXT-X-VERSION:3
#EXTINF:-1 tvg-id="" tvg-name="##### HLS ONLY #####",##### HLS ONLY #####
${config.getActualBaseUrl(call.request)}$basePath/channel.m3u8
"""
        )
    }

    get("channel.m3u8") {
        if (isNotMainEndpoint()) return@get

        call.response.headers.apply {
            append(HttpHeaders.ContentType, "audio/mpegurl")
            append(HttpHeaders.ContentDisposition, "attachment; filename=playlist.m3u8")
        }

        val baseUrl = config.getActualBaseUrl(call.request)
        val basePath = call.request.path().substringBeforeLast("/")

        call.respondText(
            """
#EXTM3U
#EXT-X-VERSION:3
#EXT-X-MEDIA-SEQUENCE:${sequenceNumber}
#EXT-X-TARGETDURATION:-1
#EXTINF:10,
${baseUrl}$basePath/hlsonly_${sequenceNumber}.ts
#EXTINF:10,
${baseUrl}$basePath/hlsonly_${sequenceNumber + 1}.ts
#EXTINF:10,
${baseUrl}$basePath/hlsonly_${sequenceNumber + 2}.ts
#EXTINF:10,
${baseUrl}$basePath/hlsonly_${sequenceNumber + 3}.ts
"""
        )
    }

    get(Regex("""hlsonly_\d+\.ts""")) {
        if (isNotMainEndpoint()) return@get

        call.respondFile(
            File(
                javaClass.getResource("/media/hlsonly.ts")?.path
                    ?: throw IllegalStateException("hlsonly.ts not found in resources")
            )
        )
    }
}
