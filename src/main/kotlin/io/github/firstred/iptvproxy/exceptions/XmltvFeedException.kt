package io.github.firstred.iptvproxy.exceptions

class XmltvFeedException(sourceName: String?, itemCount: Int, firstError: Throwable) : Exception(
    "None of the $itemCount XMLTV items of ${sourceName ?: "the feed"} could be parsed; first error: ${firstError.message}",
    firstError,
)