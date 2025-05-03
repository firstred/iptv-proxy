package io.github.firstred.iptvproxy.utils

import org.apache.commons.codec.digest.DigestUtils
import java.util.*

// Thread-safe hashing
fun String.md5(): String = DigestUtils.md5Hex(this)
fun String.sha1(): String = DigestUtils.sha1Hex(this)
fun String.sha256(): String = DigestUtils.sha256Hex(this)
fun String.sha512(): String = DigestUtils.sha512Hex(this)
fun String.hash() = sha256()

fun String.hexStringToDecimal(): Int {
    var s = this
    val digits = "0123456789ABCDEF"
    s = s.uppercase(Locale.getDefault())
    var `val` = 0
    for (i in 0..<s.length) {
        val c = s.get(i)
        val d = digits.indexOf(c)
        `val` = 16 * `val` + d
    }
    return `val`
}
