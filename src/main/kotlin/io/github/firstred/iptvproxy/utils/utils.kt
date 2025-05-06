package io.github.firstred.iptvproxy.utils

fun Boolean.toInt() = if (this) 1 else 0
fun Boolean.toUInt() = if (this) 1u else 0u
fun Int.toBoolean() = 1 == this
fun UInt.toBoolean() = 1u == this
