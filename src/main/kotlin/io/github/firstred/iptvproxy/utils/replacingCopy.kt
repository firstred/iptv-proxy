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
) {
    require(search.isNotEmpty()) { "Search sequence must not be empty" }
    require(bufferSize > 0) { "Buffer size must be positive" }

    if (search.contentEquals(replacement)) {
        copyTo(out, bufferSize)
        return
    }

    val buffer = ByteArray(bufferSize + search.size - 1)
    var length = 0

    while (true) {
        val read = read(buffer, length, buffer.size - length)
        val endOfStream = read < 0
        if (!endOfStream) length += read

        var start = 0
        var position = 0
        val lastFullPosition = length - search.size
        while (position <= lastFullPosition) {
            if (buffer[position] == search[0] && buffer.matchesAt(position, search)) {
                out.write(buffer, start, position - start)
                out.write(replacement)
                position += search.size
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
        val keepFrom = maxOf(start, length - search.size + 1)
        out.write(buffer, start, keepFrom - start)
        buffer.copyInto(buffer, 0, keepFrom, length)
        length -= keepFrom
    }
}

private fun ByteArray.matchesAt(offset: Int, search: ByteArray): Boolean {
    for (index in search.indices) {
        if (this[offset + index] != search[index]) return false
    }
    return true
}
