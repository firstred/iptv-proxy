package io.github.firstred.iptvproxy.routes

import io.github.firstred.iptvproxy.config
import io.github.firstred.iptvproxy.plugins.isNotMainEndpoint
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.io.File
import kotlin.concurrent.timer

fun Route.notices() {
    route("/notices/") {
        get("hls_only") {
            hlsOnlyNoticeStream()
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
