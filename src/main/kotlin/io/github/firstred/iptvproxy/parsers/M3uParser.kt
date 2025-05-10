package io.github.firstred.iptvproxy.parsers

import io.github.firstred.iptvproxy.dtos.m3u.M3uChannel
import io.github.firstred.iptvproxy.utils.toEncodedJavaURI
import io.ktor.http.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.IOException
import java.io.InputStream
import java.net.URISyntaxException
import java.util.regex.Matcher
import java.util.regex.Pattern
import kotlin.text.Charsets.UTF_8

class M3uParser {
    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(M3uParser::class.java)

        private val TAG_PATTERN: Pattern = Pattern.compile("#(\\w+)(?:[ :](.*))?")
        private val PROP_PATTERN: Pattern = Pattern.compile("""(?<key>[^=\s]+)="(?<value>(?:(?!"\s+[^=\s]+=).)*)"""")
        private val INFO_PATTERN: Pattern = Pattern.compile("([-+0-9]+) ?(.*)")

        fun forEachChannel(inputStream: InputStream, action: (M3uChannel) -> Unit) {
            var name = "Channel name not found"
            var props = mutableMapOf<String, String>()
            var vlcOpts = mutableMapOf<String, String>()
            var groups = mutableListOf<String>()

            fun resetVars() {
                name = "Channel name not found"
                props = mutableMapOf()
                vlcOpts = mutableMapOf()
                groups = mutableListOf()
            }

            inputStream.use { input ->
                val reader = input.bufferedReader(UTF_8)
                // First line should be #EXTM3U
                if (reader.readLine().uppercase() != "#EXTM3U") throw IOException("Invalid M3U file format")

                for (rawLine in reader.lines()) {
                    // Remove control characters and trim line
                    val line = rawLine
                        .replace(Regex("\\p{Cc}"), "")
                        .replace("\u2028", "")
                        .trim()

                    var matcher: Matcher
                    if ((TAG_PATTERN.matcher(line).also { matcher = it }).matches()) {
                        when (matcher.group(1)) {
                            "EXTM3U" -> Unit

                            "EXTINF" -> {
                                val infoLine = matcher.group(2)
                                matcher = INFO_PATTERN.matcher(infoLine)
                                if (matcher.matches()) {
                                    name = parseProps(matcher.group(2), mutableMapOf<String, String>().also { props = it }).trim()
                                    if (name.startsWith(",")) name = name.substring(1).trim()
                                } else {
                                    LOG.warn("malformed channel info: {}", infoLine)
                                    continue
                                }
                            }

                            "EXTVLCOPT" -> {
                                vlcOpts.put(matcher.group(2).substringBefore("="), matcher.group(2).substringAfter("="))
                            }

                            "EXTGRP" -> for (group in matcher.group(2).trim().split(";".toRegex()).dropLastWhile { it.isEmpty() }
                                .toTypedArray()) {
                                groups.add(group.trim())
                            }

                            else -> LOG.warn("unknown m3u tag: {}", matcher.group(1))
                        }
                    } else if (line.isNotEmpty()) {
                        props.remove("group-title")?.let { group -> groups.addAll(group.trim().split(";")) }

                        try {
                            Url(line).toEncodedJavaURI()
                            action(M3uChannel(line, name, groups, props.toMap(), vlcOpts.toMap()))
                        } catch (_: URISyntaxException) {
                            LOG.warn("malformed channel uri: {}", line)
                            resetVars()
                            continue
                        }

                        // Reset after every resource line
                        resetVars()
                    }
                }
            }
        }

        private fun parseProps(line: String, props: MutableMap<String, String>): String {
            val attrs = line.substringBeforeLast(',')
            val display = line.substringAfterLast(',')

            val m = PROP_PATTERN.matcher(attrs)

            while (m.find()) {
                props.put(m.group("key"), m.group("value"))
            }

            return display
        }
    }
}
