package io.github.firstred.iptvproxy.utils

import io.github.firstred.iptvproxy.config
import org.apache.commons.codec.digest.DigestUtils

fun String.md5(): String = DigestUtils.md5Hex(this)
fun String.sha1(): String = DigestUtils.sha1Hex(this)
fun String.sha256(): String = DigestUtils.sha256Hex(this)
fun String.sha512(): String = DigestUtils.sha512Hex(this)
fun String.hash() = sha256()

fun String.generateUserToken(): String {
    return (this + config.appSecret).hash()
}
