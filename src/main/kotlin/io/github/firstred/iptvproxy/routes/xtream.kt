package io.github.firstred.iptvproxy.routes

import io.github.firstred.iptvproxy.config
import io.github.firstred.iptvproxy.di.modules.XmltvChannelsByReference
import io.github.firstred.iptvproxy.di.modules.XmltvProgrammes
import io.github.firstred.iptvproxy.di.modules.xmltvChannelsLock
import io.github.firstred.iptvproxy.dtos.xmltv.XmltvDoc
import io.github.firstred.iptvproxy.dtos.xmltv.XmltvUtils
import io.github.firstred.iptvproxy.entities.IptvChannelType
import io.github.firstred.iptvproxy.entities.IptvUser
import io.github.firstred.iptvproxy.managers.ChannelManager
import io.github.firstred.iptvproxy.plugins.findUserFromXtreamAccountInRoutingContext
import io.github.firstred.iptvproxy.plugins.isNotMainEndpoint
import io.github.firstred.iptvproxy.plugins.isNotReady
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import jdk.internal.org.jline.utils.Colors.s
import kotlinx.coroutines.sync.withLock
import org.apache.commons.text.StringSubstitutor
import org.koin.ktor.ext.inject
import java.io.OutputStream

fun Route.xtream() {
    val channelManager: ChannelManager by inject()
    val xmltvChannelsByReference: XmltvChannelsByReference by inject()
    val xmltvProgrammes: XmltvProgrammes by inject()

    get("/xmltv.php") {
        suspend fun generateUserXmltv(substitutor: StringSubstitutor, compressed: Boolean, output: OutputStream) {
            xmltvChannelsLock.withLock {
                val newDoc = XmltvDoc(
                    channels = xmltvChannelsByReference.values.map { channel ->
                        channel.copy(icon = channel.icon.let {
                            it?.copy(
                                src = substitutor.replace(it.src),
                            )
                        })
                    }.toMutableList(),
                    programmes = xmltvProgrammes,
                )

                XmltvUtils.writeXmltv(newDoc, compressed, output)
            }
        }

        if (isNotMainEndpoint()) return@get
        if (isNotReady()) return@get

        lateinit var user: IptvUser
        try {
            user = findUserFromXtreamAccountInRoutingContext()
        } catch (_: Throwable) {
            call.respond(HttpStatusCode.Unauthorized, "Username and/or password incorrect")
            return@get
        }

        call.response.headers.apply {
            append(HttpHeaders.ContentType, ContentType.Application.Xml.toString())
            append(HttpHeaders.ContentDisposition, "attachment; filename=xmltv.xml")
        }

        // Replace the env variable placeholders in the config
        val substitutor = StringSubstitutor(mapOf(
            "BASE_URL" to config.getActualBaseUrl(call.request).toString(),
            "ENCRYPTED_ACCOUNT" to user.toEncryptedAccountHexString(),
        ))

        call.respondOutputStream { use {
            generateUserXmltv(substitutor, false, it)
            flush()
        } }
    }

    get(Regex("""/(player_api|panel_api).php""")) {
        if (isNotMainEndpoint()) return@get
        if (isNotReady()) return@get

        lateinit var user: IptvUser
        try {
            user = findUserFromXtreamAccountInRoutingContext()
        } catch (_: Throwable) {
            call.respond(HttpStatusCode.Unauthorized, "Username and/or password incorrect")
            return@get
        }

        call.response.headers.apply {
            append(HttpHeaders.ContentType, "audio/mpegurl")
            append(HttpHeaders.ContentDisposition, "attachment; filename=playlist.m3u8")
        }

        when {
            call.request.queryParameters["action"] == "get_live_streams" -> {
                call.respondOutputStream { use { output ->
                    channelManager.getAllChannelsPlaylist(
                        output,
                        user,
                        config.getActualBaseUrl(call.request),
                        IptvChannelType.LIVE,
                    )
                    output.flush()
                } }
            }

            call.request.queryParameters["action"] == "get_series" -> {
                call.respondOutputStream { use { output ->
                    channelManager.getAllChannelsPlaylist(
                        output,
                        user,
                        config.getActualBaseUrl(call.request),
                        IptvChannelType.SERIES,
                    )
                    output.flush()
                } }
            }

            listOf("get_vod_streams", "get_movies_streams").contains(call.request.queryParameters["action"]) -> {
                call.respondOutputStream { use { output ->
                    channelManager.getAllChannelsPlaylist(
                        output,
                        user,
                        config.getActualBaseUrl(call.request),
                        IptvChannelType.MOVIE,
                    )
                    output.flush()
                } }
            }

            else -> {
                call.respond(HttpStatusCode.BadRequest, "Invalid action")
            }
        }
    }

    get("/get.php") {
        if (isNotMainEndpoint()) return@get
        if (isNotReady()) return@get

        lateinit var user: IptvUser
        try {
            user = findUserFromXtreamAccountInRoutingContext()
        } catch (_: Throwable) {
            call.respond(HttpStatusCode.Unauthorized, "Username and/or password incorrect")
            return@get
        }

        call.response.headers.apply {
            append(HttpHeaders.ContentType, "audio/mpegurl")
            append(HttpHeaders.ContentDisposition, "attachment; filename=playlist.m3u8")
        }

        call.respondOutputStream { use { output ->
            channelManager.getAllChannelsPlaylist(
                output,
                user,
                config.getActualBaseUrl(call.request),
            )
            output.flush()
        } }
    }
}
