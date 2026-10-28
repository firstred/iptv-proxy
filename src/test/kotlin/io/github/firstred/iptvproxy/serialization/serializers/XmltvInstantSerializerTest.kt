package io.github.firstred.iptvproxy.serialization.serializers

import io.github.firstred.iptvproxy.dtos.xmltv.XmltvProgramme
import io.github.firstred.iptvproxy.serialization.xml
import kotlinx.serialization.SerializationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Instant

class XmltvInstantSerializerTest {
    @Test
    fun `parses timestamps with and without offset`() {
        assertEquals(Instant.parse("2026-10-28T01:35:00Z"), XmltvInstantSerializer.parse("20261028023500 +0100"))
        assertEquals(Instant.parse("2026-10-28T01:35:00Z"), XmltvInstantSerializer.parse("20261028023500+0100"))
        assertEquals(Instant.parse("2026-10-28T02:35:00Z"), XmltvInstantSerializer.parse("20261028023500"))
    }

    @Test
    fun `carries over rolled over time values`() {
        assertEquals(Instant.parse("2026-10-28T03:00:00Z"), XmltvInstantSerializer.parse("20261028026000 +0000"))
        assertEquals(Instant.parse("2026-10-28T03:00:00Z"), XmltvInstantSerializer.parse("20261028025960 +0000"))
        assertEquals(Instant.parse("2026-10-31T23:00:00Z"), XmltvInstantSerializer.parse("20261031240000 +0100"))
        assertEquals(Instant.parse("2027-01-01T00:00:00Z"), XmltvInstantSerializer.parse("20261231235960 +0000"))
    }

    @Test
    fun `error contains the actual value`() {
        listOf("20261028027000 +0000", "20261328020000 +0000", "not-a-timestamp").forEach { value ->
            val exception = assertFailsWith<SerializationException> { XmltvInstantSerializer.parse(value) }
            assertTrue(exception.message!!.contains("\"$value\""), exception.message)
        }

        val exception = assertFailsWith<SerializationException> { XmltvInstantSerializer.parse("20261028027000 +0000") }
        assertTrue(exception.message!!.contains("Invalid value for MinuteOfHour"), exception.message)
    }

    @Test
    fun `decodes programmes with rolled over and reports invalid timestamps`() {
        val programme = xml.decodeFromString(
            XmltvProgramme.serializer(),
            """<programme start="20261028026000 +0000" stop="20261028030000 +0000" channel="channel"/>""",
        )
        assertEquals(Instant.parse("2026-10-28T03:00:00Z"), programme.start)
        assertEquals(programme.start, programme.stop)

        val exception = assertFails {
            xml.decodeFromString(
                XmltvProgramme.serializer(),
                """<programme start="20261028020000 +0000" stop="20261028027000 +0000" channel="channel"/>""",
            )
        }
        assertTrue(exception.message!!.contains("\"20261028027000 +0000\""), exception.message)
    }
}
