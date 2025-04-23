package io.github.firstred.iptvproxy.utils.base64

import java.util.*

fun String.decodeBase64UrlString(): String = Base64.getUrlDecoder().decode(this)?.let { String(it) }
    ?: throw IllegalArgumentException("Invalid Base64 URL encoded string")
