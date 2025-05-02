package io.github.firstred.iptvproxy.utils.base64

import java.net.URI
import java.util.*
import kotlin.text.Charsets.UTF_8

fun String.encodeToBase64UrlString(): String = Base64.getUrlEncoder().encodeToString(toByteArray(UTF_8))
fun URI.encodeToBase64UrlString(): String = toString().encodeToBase64UrlString()
