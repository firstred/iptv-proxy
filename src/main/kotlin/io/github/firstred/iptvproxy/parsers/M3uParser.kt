package io.github.firstred.iptvproxy.parsers

import io.github.firstred.iptvproxy.dtos.m3u.M3uChannel
import io.github.firstred.iptvproxy.dtos.m3u.M3uDoc
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.InputStream
import java.net.URI
import java.net.URISyntaxException
import java.util.regex.Matcher
import java.util.regex.Pattern
import kotlin.text.Charsets.UTF_8

object M3uParser {
    private val LOG: Logger = LoggerFactory.getLogger(M3uParser::class.java)

    private val TAG_PAT: Pattern = Pattern.compile("#(\\w+)(?:[ :](.*))?")
    private val PROP_PAT: Pattern = Pattern.compile(" *([\\w-_]+)=\"([^\"]*)\"(.*)")
    private val PROP_NONSTD_PAT: Pattern = Pattern.compile(" *([\\w-_]+)=([^\"][^ ]*)(.*)")
    private val INFO_PAT: Pattern = Pattern.compile("([-+0-9]+) ?(.*)")

    fun parse(inputStream: InputStream): M3uDoc? {
        var m3uProps = emptyMap<String, String>()
        val channels: MutableList<M3uChannel> = ArrayList()

        var groups: MutableSet<String> = HashSet()
        var props: MutableMap<String, String>? = null
        var name: String? = null

        inputStream.use {
            it.bufferedReader(UTF_8).useLines { lines -> lines.forEach {
                val line = it.trim()

                var m: Matcher

                if ((TAG_PAT.matcher(line).also { m = it }).matches()) {
                    when (m.group(1)) {
                        "EXTM3U" -> {
                            val p = m.group(2)
                            if (p != null) {
                                val prop = parseProps(m.group(2), mutableMapOf<String, String>().also { m3uProps = it }).trim()
                                if (prop.isNotEmpty()) {
                                    LOG.warn("malformed property: {}", prop)
                                }
                            }
                        }

                        "EXTINF" -> {
                            val infoLine = m.group(2)
                            m = INFO_PAT.matcher(infoLine)
                            if (m.matches()) {
                                name = parseProps(m.group(2), mutableMapOf<String, String>().also { props = it }).trim()
                                if (name!!.startsWith(",")) {
                                    name = name!!.substring(1).trim()
                                }
                            } else {
                                LOG.error("malformed channel info: {}", infoLine)
                                return null
                            }
                        }

                        "EXTGRP" -> for (group in m.group(2).trim().split(";".toRegex()).dropLastWhile { it.isEmpty() }
                            .toTypedArray()) {
                            groups.add(group.trim())
                        }

                        else -> LOG.warn("unknown m3u tag: {}", m.group(1))
                    }
                } else if (line.isNotEmpty()) {
                    var isLive = false
                    // Check if line starts with http(s) or rtsp
                    try {
                        if (URI(line).path.startsWith("/live/")) isLive = true
                    } catch (ignored: URISyntaxException) {
                    }

                    if (isLive) {
                        if (name == null) {
                            LOG.warn("url found while no info defined: {}", line)
                        } else {
                            val group = props!!.remove("group-title")
                            if (group != null) {
                                groups.add(group)
                            }

                            channels.add(M3uChannel(line, name!!, groups.toSet(), props!!.toMap()))
                        }
                    }

                    name = null
                    groups = HashSet()
                    props = null
                }
            } }
        }

        return M3uDoc(channels.toList(), m3uProps)
    }

    private fun parseProps(line: String, props: MutableMap<String, String>): String {
        @Suppress("NAME_SHADOWING") var line = line
        var postfix = ""
        val malformedProps: MutableList<String> = ArrayList()

        while (line.isNotEmpty()) {
            var m = PROP_PAT.matcher(line)
            if (!m.matches()) {
                m = PROP_NONSTD_PAT.matcher(line)
            }
            if (m.matches()) {
                props[m.group(1)] = m.group(2)
                line = m.group(3).trim()
                postfix = line

                if (malformedProps.isNotEmpty()) {
                    malformedProps.forEach({ prop: String? -> LOG.warn("malformed property: {}", prop) })
                    malformedProps.clear()
                }
            } else {
                // try to continue parsing properties
                var idx = line.indexOf(' ')
                if (idx < 0) {
                    idx = line.length
                }

                malformedProps.add(line.substring(0, idx))

                line = line.substring(idx).trim()
            }
        }

        return postfix
    }
}
