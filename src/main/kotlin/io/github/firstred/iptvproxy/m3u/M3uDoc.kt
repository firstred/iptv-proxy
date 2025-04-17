package io.github.firstred.iptvproxy.m3u

class M3uDoc(@JvmField val channels: List<M3uChannel>, val props: Map<String, String>) {
    fun getProp(key: String, value: String?): String? {
        return props[key]
    }
}
