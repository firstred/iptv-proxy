package io.github.firstred.iptvproxy.routes

import io.github.firstred.iptvproxy.config
import io.github.firstred.iptvproxy.entities.IptvUser
import io.github.firstred.iptvproxy.managers.ChannelManager
import io.github.firstred.iptvproxy.plugins.findUserFromEncryptedAccountInRoutingContext
import io.github.firstred.iptvproxy.plugins.isNotMainEndpoint
import io.github.firstred.iptvproxy.plugins.isNotReady
import io.github.firstred.iptvproxy.plugins.withUserPermit
import io.github.firstred.iptvproxy.utils.filterHttpRequestHeaders
import io.github.firstred.iptvproxy.utils.filterHttpResponseHeaders
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

fun Route.live() {
    val channelManager: ChannelManager by inject()

    route("/live/") {
        get(Regex("""(?<encryptedaccount>[0-9a-fA-F]+)/(?<channel>[a-z0-f]+)/[^.]*\.(?<extension>.*)$""")) {
            if (isNotMainEndpoint()) return@get
            if (isNotReady()) return@get

            lateinit var user: IptvUser
            try {
                user = findUserFromEncryptedAccountInRoutingContext()
            } catch (_: Throwable) {
                call.respond(HttpStatusCode.Unauthorized, "Username and/or password incorrect")
                return@get
            }

            val channelId = call.parameters["channel"] ?: ""

            withUserPermit(user) {
                call.response.headers.apply {
                    append(HttpHeaders.ContentType, "audio/mpegurl")
                    append(
                        HttpHeaders.ContentDisposition,
                        "attachment; filename=${call.request.path().substringAfterLast("/")}",
                    )
                }

                call.respondText(
                    channelManager.getChannelPlaylist(
                    channelId,
                    user,
                    config.getActualBaseUrl(call.request),
                    call.request.headers.filterHttpRequestHeaders(),
                    call.request.queryParameters,
                    ) { headers -> headers.filterHttpResponseHeaders().entries().forEach { (key, value) -> value.forEach { call.response.headers.append(key, it) } } })
            }
        }
    }
}
