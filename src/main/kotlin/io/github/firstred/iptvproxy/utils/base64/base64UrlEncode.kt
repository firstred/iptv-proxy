package io.github.firstred.iptvproxy.utils.base64

import java.net.URI
import java.util.*

fun String.encodeToBase64UrlString(): String = Base64.getUrlEncoder().encodeToString(toByteArray())
fun URI.encodeToBase64UrlString(): String = toString().encodeToBase64UrlString()
