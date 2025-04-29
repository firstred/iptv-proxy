package io.github.firstred.iptvproxy.dtos.m3u

data class M3uChannel(val url: String, val name: String, val groups: List<String>, val props: Map<String, String>)
