package io.github.firstred.iptvproxy.parsers

import io.github.firstred.iptvproxy.dtos.xmltv.XmltvChannel
import io.github.firstred.iptvproxy.dtos.xmltv.XmltvProgramme
import io.github.firstred.iptvproxy.exceptions.XmltvFeedException
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class XmltvParserTest {
    @Test
    fun `skips broken programmes and continues parsing the feed`() {
        val channels = mutableListOf<XmltvChannel>()
        val programmes = mutableListOf<XmltvProgramme>()
        val xmltv = """
            <tv>
                <programme start="20261028020000 +0000" stop="20261028030000 +0000" channel="before"/>
                <programme start="20261028027000 +0000" stop="20261028030000 +0000" channel="broken-self-closing"/>
                <programme start="20261028027000 +0000" stop="20261028030000 +0000" channel="broken-with-children">
                    <title>Broken programme</title>
                </programme>
                <channel id="after"/>
                <programme start="20261028030000 +0000" stop="20261028040000 +0000" channel="after"/>
            </tv>
        """.trimIndent()

        val result = XmltvParser.forEachXmltvItem(
            ByteArrayInputStream(xmltv.toByteArray()),
            onHeader = {},
            onChannel = channels::add,
            onProgramme = programmes::add,
            sourceName = "test-feed",
        )

        assertEquals(listOf("after"), channels.map { it.id })
        assertEquals(listOf("before", "after"), programmes.map { it.channel })
        assertEquals(1, result.channels)
        assertEquals(2, result.programmes)
        assertEquals(0, result.skippedChannels)
        assertEquals(2, result.skippedProgrammes)
    }

    @Test
    fun `fails a feed when every item is broken`() {
        val value = "20261028027000 +0000"
        val exception = assertFailsWith<XmltvFeedException> {
            XmltvParser.forEachXmltvItem(
                ByteArrayInputStream(
                    "<tv><programme start=\"$value\" stop=\"20261028030000 +0000\" channel=\"broken\"/></tv>"
                        .toByteArray(),
                ),
                onHeader = {},
                onChannel = {},
                onProgramme = {},
                sourceName = "test-feed",
            )
        }

        assertTrue(exception.message!!.contains("None of the 1 XMLTV items"), exception.message)
        assertTrue(exception.message!!.contains("test-feed"), exception.message)
        assertTrue(exception.message!!.contains(value), exception.message)
    }

    @Test
    fun `empty and valid feeds return their parse counts`() {
        val emptyResult = XmltvParser.forEachXmltvItem(
            ByteArrayInputStream("<tv/>".toByteArray()),
            onHeader = {},
            onChannel = {},
            onProgramme = {},
        )
        assertEquals(0, emptyResult.importedItems)
        assertEquals(0, emptyResult.skippedItems)

        val validResult = XmltvParser.forEachXmltvItem(
            ByteArrayInputStream(
                """<tv><channel id="channel"/><programme start="20261028020000 +0000" stop="20261028030000 +0000" channel="channel"/></tv>"""
                    .toByteArray(),
            ),
            onHeader = {},
            onChannel = {},
            onProgramme = {},
        )
        assertEquals(1, validResult.channels)
        assertEquals(1, validResult.programmes)
        assertEquals(2, validResult.importedItems)
        assertEquals(0, validResult.skippedItems)
    }

    @Test
    fun `malformed xml propagates`() {
        assertFailsWith<IOException> {
            XmltvParser.forEachXmltvItem(
                ByteArrayInputStream("<tv><channel id=\"channel\"/></broken>".toByteArray()),
                onHeader = {},
                onChannel = {},
                onProgramme = {},
            )
        }
    }

    @Test
    fun `input stream failures propagate`() {
        val inputStream = object : InputStream() {
            override fun read(): Int = throw IOException("read failed")
        }

        val exception = assertFailsWith<IOException> {
            XmltvParser.forEachXmltvItem(
                inputStream,
                onHeader = {},
                onChannel = {},
                onProgramme = {},
            )
        }
        assertTrue(exception.message!!.contains("read failed"), exception.message)
    }

    @Test
    fun `callback failures propagate`() {
        val exception = assertFailsWith<IllegalStateException> {
            XmltvParser.forEachXmltvItem(
                ByteArrayInputStream(
                    """<tv><programme start="20261028020000 +0000" stop="20261028030000 +0000" channel="channel"/></tv>"""
                        .toByteArray(),
                ),
                onHeader = {},
                onChannel = {},
                onProgramme = { throw IllegalStateException("callback failed") },
            )
        }
        assertEquals("callback failed", exception.message)
    }
}