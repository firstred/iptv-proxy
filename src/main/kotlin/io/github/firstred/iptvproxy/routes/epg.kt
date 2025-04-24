package io.github.firstred.iptvproxy.routes

import io.github.firstred.iptvproxy.config
import io.github.firstred.iptvproxy.dtos.xmltv.XmltvUtils
import io.github.firstred.iptvproxy.plugins.findUserFromRoutingContext
import io.github.firstred.iptvproxy.plugins.isNotMainEndpoint
import io.github.firstred.iptvproxy.plugins.isNotReady
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.runBlocking
import org.apache.commons.text.StringSubstitutor
import java.io.OutputStream

fun Route.epg() {
    fun generateUserXmltv(substitutor: StringSubstitutor, compressed: Boolean, output: OutputStream) {
        XmltvUtils.xmltvInputStream(compressed = false).use { input ->
            val xmltvDoc = XmltvUtils.parseXmltv(input)
            xmltvDoc.channels?.forEachIndexed { idx, channel ->
                xmltvDoc.channels!![idx] = channel.copy(
                    icon = channel.icon.let {
                        it?.copy(
                            src = substitutor.replace(it.src)
                        )
                    }
                )
            }

            runBlocking { XmltvUtils.writeXmltv(xmltvDoc, compressed, output) }
        }
    }

    route("/epg/") {
        get(Regex("""(?<username>[a-zA-Z0-9-]+)_(?<password>[^/]+)/xmltv\.xml""")) {
            if (isNotMainEndpoint()) return@get
            if (isNotReady()) return@get
            val user = findUserFromRoutingContext()

            call.response.headers.apply {
                append(HttpHeaders.ContentType, ContentType.Application.Xml.toString())
                append(HttpHeaders.ContentDisposition, "attachment; filename=xmltv.xml")
            }

            // Replace the env variable placeholders in the config
            val substitutor = StringSubstitutor(mapOf(
                "BASE_URL" to config.getActualBaseUrl(call.request).toString(),
                "ENCRYPTED_ACCOUNT" to user.toEncryptedAccountHexString(),
            ))

            call.respondOutputStream { use { generateUserXmltv(substitutor, false, it) } }
        }

        get(Regex("""(?<username>[a-zA-Z0-9-]+)_(?<password>[^/]+)/xmltv\.xml\.gz""")) {
            if (isNotMainEndpoint()) return@get
            val user = findUserFromRoutingContext()
            if (isNotReady()) return@get

            call.response.headers.apply {
                append(HttpHeaders.ContentType, ContentType.Application.Xml.toString())
                append(HttpHeaders.ContentEncoding, "gzip")
                append(HttpHeaders.ContentDisposition, "attachment; filename=xmltv.xml.gz")
            }

            // Replace the env variable placeholders in the config
            val substitutor = StringSubstitutor(mapOf(
                "BASE_URL" to config.getActualBaseUrl(call.request).toString(),
                "ENCRYPTED_ACCOUNT" to user.toEncryptedAccountHexString(),
            ))

            call.respondOutputStream { use { generateUserXmltv(substitutor, true, it) } }
        }
    }
}

