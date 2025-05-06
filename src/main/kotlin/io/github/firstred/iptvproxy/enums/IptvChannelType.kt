package io.github.firstred.iptvproxy.enums

enum class IptvChannelType(val type: String) {
    live("live"),
    movie("movie"),
    series("series"),
    radio_streams("radio_streams");

    fun urlType(): IptvChannelType = when (this) {
        live -> live
        movie -> movie
        series -> series
        else -> live
    }

    companion object {
        const val maxDbLength = 31
    }
}
