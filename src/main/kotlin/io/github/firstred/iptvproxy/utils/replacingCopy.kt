package io.github.firstred.iptvproxy.utils

import java.io.InputStream
import java.io.OutputStream

/**
 * Copies this stream to [out] while replacing every occurrence of [search] with [replacement].
 *
 * The stream is processed in chunks; up to `search.size - 1` bytes are carried over between chunks so occurrences
 * spanning a chunk boundary are replaced as well.
 */
fun InputStream.copyReplacingTo(
    out: OutputStream,
    search: ByteArray,
    replacement: ByteArray,
    bufferSize: Int = DEFAULT_BUFFER_SIZE,
) = copyReplacingTo(out, listOf(search to replacement), bufferSize)

/**
 * Copies this stream to [out] while replacing every occurrence of each search sequence with its replacement.
 *
 * Occurrences are replaced from left to right without overlapping; when multiple search sequences match at the same
 * position, the first one in [replacements] wins. Up to `longest search sequence - 1` bytes are carried over between
 * chunks so occurrences spanning a chunk boundary are replaced as well.
 */
fun InputStream.copyReplacingTo(
    out: OutputStream,
    replacements: List<Pair<ByteArray, ByteArray>>,
    bufferSize: Int = DEFAULT_BUFFER_SIZE,
) {
    require(replacements.all { (search, _) -> search.isNotEmpty() }) { "Search sequence must not be empty" }
    require(bufferSize > 0) { "Buffer size must be positive" }

    val activeReplacements = replacements.filterNot { (search, replacement) -> search.contentEquals(replacement) }
    if (activeReplacements.isEmpty()) {
        copyTo(out, bufferSize)
        return
    }

    val longestSearch = activeReplacements.maxOf { (search, _) -> search.size }
    val firstBytes = BooleanArray(256).apply {
        activeReplacements.forEach { (search, _) -> this[search[0].toInt() and 0xff] = true }
    }
    val buffer = ByteArray(bufferSize + longestSearch - 1)
    var length = 0

    while (true) {
        val read = read(buffer, length, buffer.size - length)
        val endOfStream = read < 0
        if (!endOfStream) length += read

        var start = 0
        var position = 0
        // Positions closer to the end than the longest search sequence are checked once more data is available
        val lastPosition = if (endOfStream) length - 1 else length - longestSearch
        while (position <= lastPosition) {
            val match = if (firstBytes[buffer[position].toInt() and 0xff]) {
                activeReplacements.firstOrNull { (search, _) -> buffer.matchesAt(position, length, search) }
            } else null

            if (null != match) {
                out.write(buffer, start, position - start)
                out.write(match.second)
                position += match.first.size
                start = position
            } else {
                position++
            }
        }

        if (endOfStream) {
            out.write(buffer, start, length - start)
            return
        }

        // Keeps a possible partial match for the next chunk
        out.write(buffer, start, position - start)
        buffer.copyInto(buffer, 0, position, length)
        length -= position
    }
}

private fun ByteArray.matchesAt(offset: Int, length: Int, search: ByteArray): Boolean {
    if (offset + search.size > length) return false
    for (index in search.indices) {
        if (this[offset + index] != search[index]) return false
    }
    return true
}
