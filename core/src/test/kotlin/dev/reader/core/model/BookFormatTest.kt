package dev.reader.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BookFormatTest {

    @Test
    fun `maps known extensions case-insensitively`() {
        assertEquals(BookFormat.EPUB, BookFormat.fromExtension("EPUB"))
        assertEquals(BookFormat.MARKDOWN, BookFormat.fromExtension("md"))
        assertEquals(BookFormat.HTML, BookFormat.fromExtension("xhtml"))
        assertEquals(BookFormat.CBR, BookFormat.fromExtension("cbr"))
        assertEquals(BookFormat.FB2, BookFormat.fromExtension("fb2"))
        assertEquals(BookFormat.MOBI, BookFormat.fromExtension("mobi"))
        assertEquals(BookFormat.MOBI, BookFormat.fromExtension("azw"))
        assertEquals(BookFormat.MOBI, BookFormat.fromExtension("AZW3"))
        assertEquals(BookFormat.RTF, BookFormat.fromExtension("rtf"))
        assertEquals(BookFormat.DOCX, BookFormat.fromExtension("docx"))
    }

    @Test
    fun `returns null for unknown extensions`() {
        assertNull(BookFormat.fromExtension("azw4"))
        assertNull(BookFormat.fromExtension("kfx"))
    }
}
