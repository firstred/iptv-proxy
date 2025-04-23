package io.github.firstred.iptvproxy.utils

import io.github.firstred.iptvproxy.config
import java.security.MessageDigest

private object Digest {
    val md5: MessageDigest by lazy {
        MessageDigest.getInstance("MD5")
    }
    val sha1: MessageDigest by lazy {
        MessageDigest.getInstance("SHA-1")
    }
    val sha256: MessageDigest by lazy {
        MessageDigest.getInstance("SHA-256")
    }
    val sha512: MessageDigest by lazy {
        MessageDigest.getInstance("SHA-512")
    }
}

fun String.md5(): String = digest(this, Digest.md5)
fun String.sha1(): String = digest(this, Digest.sha1)
fun String.sha256(): String = digest(this, Digest.sha256)
fun String.sha512(): String = digest(this, Digest.sha512)
fun String.hash() = sha256()

fun String.generateUserToken(): String {
    return (this + config.appSecret).hash()
}

@OptIn(ExperimentalStdlibApi::class)
private fun digest(string: String, messageDigest: MessageDigest): String {
    messageDigest.update(string.toByteArray())
    val digest = messageDigest.digest()
    messageDigest.reset()
    return digest.toHexString()
}
