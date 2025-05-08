package io.github.firstred.iptvproxy.parsers

import io.github.firstred.iptvproxy.dtos.m3u.M3uChannel
import io.github.firstred.iptvproxy.dtos.m3u.M3uDoc
import io.github.firstred.iptvproxy.utils.toEncodedJavaURI
import io.ktor.http.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
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

        fun parse(inputStream: InputStream): M3uDoc? {
            val channels: MutableList<M3uChannel> = ArrayList()
            var name = "Channel name not found"
            var m3uProps = mutableMapOf<String, String>()
            var props = mutableMapOf<String, String>()
            var vlcOpts = mutableMapOf<String, String>()
            var groups = mutableListOf<String>()

            fun resetVars() {
                name = "Channel name not found"
                m3uProps = mutableMapOf()
                props = mutableMapOf()
                vlcOpts = mutableMapOf()
                groups = mutableListOf()
            }

            inputStream.use { input -> for (rawLine in input.bufferedReader(UTF_8).lines()) {
                // Remove control characters and trim line
                val line = rawLine
                    .replace(Regex("\\p{Cc}"), "")
                    .replace("\u2028", "")
                    .trim()

                var matcher: Matcher
                if ((TAG_PATTERN.matcher(line).also { matcher = it }).matches()) {
                    when (matcher.group(1)) {
                        "EXTM3U" -> {
                            val p = matcher.group(2)
                            if (p != null) {
                                val prop = parseProps(matcher.group(2), mutableMapOf<String, String>().also { m3uProps = it }).trim()
                                if (prop.isNotEmpty()) LOG.warn("malformed property: {}", prop)
                            }
                        }

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
                        channels.add(M3uChannel(line, name, groups, props.toMap(), vlcOpts.toMap()))
                    } catch (_: URISyntaxException) {
                        LOG.warn("malformed channel uri: {}", line)
                        resetVars()
                        continue
                    }

                    // Reset after every resource line
                    resetVars()
                }
            } }

            return M3uDoc(channels.toList(), m3uProps)
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
