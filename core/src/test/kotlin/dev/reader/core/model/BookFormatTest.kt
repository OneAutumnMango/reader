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
    }

    @Test
    fun `returns null for unknown extensions`() {
        assertNull(BookFormat.fromExtension("mobi"))
    }
}
