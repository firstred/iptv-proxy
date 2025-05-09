package io.github.firstred.iptvproxy.utils

import io.github.z4kn4fein.semver.Version

fun Version.toInt(): Int {
    val regex = Regex("""([0-9.]+)\.([0-9.]+)\.([0-9.]+)""")

    return regex.matchEntire(toString())?.let {
        val major = it.groupValues[1].toInt()
        val minor = it.groupValues[2].toInt()
        val patch = it.groupValues[3].toInt()

        major * 1000000 + minor * 10000 + patch
    } ?: throw IllegalArgumentException("Invalid version format: $this")
}

fun Version.fromInt(version: Int): Version {
    val major = version / 1000000
    val minor = (version % 1000000) / 10000
    val patch = version % 10000
    return Version(major, minor, patch)
}
