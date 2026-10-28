package io.github.firstred.iptvproxy.dtos.xmltv

import io.github.firstred.iptvproxy.serialization.xml
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class XmltvAudioTest {
    private fun decodeProgramme(audio: String): XmltvProgramme = xml.decodeFromString(
        XmltvProgramme.serializer(),
        """<programme start="20261026000000 +0000" stop="20261026010000 +0000" channel="MUSIC.BOX.00s.musicbox">""" +
            "<title>Music</title>$audio</programme>",
    )

    @Test
    fun `stereo value is read from the element text`() {
        val programme = decodeProgramme("<audio><stereo>stereo</stereo></audio>")

        assertEquals(listOf("stereo"), programme.audio?.stereo?.map { it.value })
    }

    @Test
    fun `empty stereo element does not fail the programme`() {
        assertEquals(listOf(""), decodeProgramme("<audio><stereo/></audio>").audio?.stereo?.map { it.value })
        assertEquals(listOf(""), decodeProgramme("<audio><stereo></stereo></audio>").audio?.stereo?.map { it.value })
    }

    @Test
    fun `audio without stereo and present element are accepted`() {
        assertEquals(listOf(), decodeProgramme("<audio/>").audio?.stereo)
        assertEquals(listOf(), decodeProgramme("<audio><present>yes</present></audio>").audio?.stereo)
        assertNull(decodeProgramme("").audio)
    }

    @Test
    fun `stereo value is written as element text`() {
        val encoded = xml.encodeToString(
            XmltvProgramme.serializer(),
            decodeProgramme("<audio><stereo>dolby digital</stereo></audio>"),
        )

        assertTrue(encoded.contains("<stereo>dolby digital</stereo>"), encoded)
    }
}
