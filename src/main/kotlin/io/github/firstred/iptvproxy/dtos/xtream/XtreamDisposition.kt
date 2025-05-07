package io.github.firstred.iptvproxy.dtos.xtream

import kotlinx.serialization.Serializable

@Serializable
data class XtreamDisposition(
    val default: UInt = 0u,
    val dub: UInt = 0u,
    val original: UInt = 0u,
    val comment: UInt = 0u,
    val lyrics: UInt = 0u,
    val karaoke: UInt = 0u,
    val forced: UInt = 0u,
    val hearingImpaired: UInt = 0u,
    val visualImpaired: UInt = 0u,
    val cleanEffects: UInt = 0u,
    val attachedPic: UInt = 0u,
    val timedThumbnails: UInt = 0u,
    val captions: UInt = 0u,
    val descriptions: UInt = 0u,
    val metadata: UInt = 0u,
    val dependent: UInt = 0u,
    val stillImage: UInt = 0u,
)
