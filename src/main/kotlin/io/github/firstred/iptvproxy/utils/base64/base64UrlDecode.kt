package io.github.firstred.iptvproxy.utils.base64

import java.net.URI
import java.util.*

fun String.encodeBase64UrlString(): String = Base64.getUrlEncoder().encodeToString(toByteArray())
fun URI.encodeBase64UrlString(): String = toString().encodeBase64UrlString()
