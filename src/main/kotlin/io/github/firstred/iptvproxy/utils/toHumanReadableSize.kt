package io.github.firstred.iptvproxy.utils

fun String.toHumanReadableSize(): String = this.toDoubleOrNull()?.toHumanReadableSize() ?: this
fun Double.toHumanReadableSize(): String = when {
    this >= 1 shl 30 -> "%.1f GiB".format(this / (1 shl 30))
    this >= 1 shl 20 -> "%.1f MiB".format(this / (1 shl 20))
    this >= 1 shl 10 -> "%.0f kiB".format(this / (1 shl 10))
    else -> "$this bytes"
}
