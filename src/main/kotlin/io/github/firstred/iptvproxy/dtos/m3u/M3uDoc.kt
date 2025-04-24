package io.github.firstred.iptvproxy.dtos.m3u

data class M3uDoc(val channels: List<M3uChannel>, val props: Map<String, String>)
