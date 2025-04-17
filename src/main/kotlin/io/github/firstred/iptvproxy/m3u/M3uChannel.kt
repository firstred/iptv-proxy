package io.github.firstred.iptvproxy.m3u

class M3uChannel(val url: String, val name: String, val groups: Set<String>, val props: Map<String, String>) {
    fun getProp(key: String): String? {
        return props[key]
    }
}
