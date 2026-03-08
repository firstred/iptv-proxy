package io.github.firstred.iptvproxy.utils

fun appVersionToInt(version: String): Int {
    val regex = """^(\d+)\.(\d+)\.(\d+)(?:-([a-zA-Z]+)(\d+))?$""".toRegex()
    val match = regex.find(version) ?: return 1

    val major = match.groups[1]?.value?.toInt() ?: 0
    val minor = match.groups[2]?.value?.toInt() ?: 0
    val patch = match.groups[3]?.value?.toInt() ?: 0

    val stageString = match.groups[4]?.value?.lowercase()
    val stageNumber = match.groups[5]?.value?.toInt() ?: 0

    val stagePrefix = when (stageString) {
        "alpha" -> 0
        "beta"  -> 1
        "rc"    -> 2 // Updated to 2
        null    -> 9 // 9 ensures stable versions are numerically higher than pre-releases
        else    -> 0
    }

    // Format: 2 digits for Major, Minor, Patch. 1 digit for stage, 2 digits for stageNumber
    val paddedString = "%02d%02d%02d%d%02d".format(
        major, minor, patch, stagePrefix, stageNumber
    )

    return paddedString.toInt()
}
