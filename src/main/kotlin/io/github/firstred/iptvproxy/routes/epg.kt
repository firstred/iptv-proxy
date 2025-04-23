package io.github.firstred.iptvproxy.routes

import io.github.firstred.iptvproxy.config
import io.github.firstred.iptvproxy.dtos.xmltv.XmltvUtils
import io.github.firstred.iptvproxy.plugins.isNotAuthenticated
import io.github.firstred.iptvproxy.plugins.isNotMainEndpoint
import io.github.firstred.iptvproxy.plugins.isNotReady
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject
import java.io.File

fun Route.epg() {
    val xmltvUtils: XmltvUtils by inject()

    route("/epg/") {
        get(Regex("""(?<username>[a-zA-Z0-9-]+)_(?<password>[^/]+)/xmltv\.xml""")) {
            if (isNotMainEndpoint()) return@get
            if (isNotAuthenticated(call.parameters["username"], password = call.parameters["password"])) return@get
            if (isNotReady()) return@get

            call.response.headers.apply {
                append(HttpHeaders.ContentType, ContentType.Application.Xml.toString())
                append(HttpHeaders.ContentDisposition, "attachment; filename=xmltv.xml")
            }

            call.respondOutputStream {
                use { output ->
                    XmltvUtils.xmltvInputStream(compressed = false).use { input -> input.copyTo(output) }
                }
            }
        }

        get(Regex("""(?<username>[a-zA-Z0-9-]+)_(?<password>[^/]+)/xmltv\.xml\.gz""")) {
            if (isNotMainEndpoint()) return@get
            if (isNotAuthenticated(call.parameters["username"], password = call.parameters["password"])) return@get
            if (isNotReady()) return@get

            call.response.headers.apply {
                append(HttpHeaders.ContentType, ContentType.Application.Xml.toString())
                append(HttpHeaders.ContentEncoding, "gzip")
                append(HttpHeaders.ContentDisposition, "attachment; filename=xmltv.xml.gz")
            }

            call.respondFile(File(config.getCacheDirectory() + "/xmltv.xml.gz"))
        }
    }
}

