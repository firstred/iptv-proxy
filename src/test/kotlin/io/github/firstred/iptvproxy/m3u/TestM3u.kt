package io.github.firstred.iptvproxy.m3u

import io.github.firstred.iptvproxy.parsers.M3uParser.parse
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path

object TestM3u {
    private val LOG: Logger = LoggerFactory.getLogger(TestM3u::class.java)

    @JvmStatic
    fun main(args: Array<String>) {
        try {
            val path = "ilook.m3u8"

            val content = Files.readString(Path.of(path))

            val doc = checkNotNull(parse(content))
            LOG.info("channels found: {}", doc.channels.size)
        } catch (e: Exception) {
            LOG.error("error", e)
        }
    }
}
