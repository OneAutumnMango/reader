package dev.reader.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BookFormatTest {

    @Test
    fun `maps known extensions case-insensitively`() {
        assertEquals(BookFormat.EPUB, BookFormat.fromExtension("EPUB"))
        listOf("txt", "text", "nfo", "log").forEach {
            assertEquals(BookFormat.TXT, BookFormat.fromExtension(it))
        }
        listOf("md", "markdown", "mdown", "mkd", "mkdn", "mdwn", "mdtxt", "rmd", "qmd").forEach {
            assertEquals(BookFormat.MARKDOWN, BookFormat.fromExtension(it))
        }
        listOf("htm", "html", "xhtml", "xht", "shtml").forEach {
            assertEquals(BookFormat.HTML, BookFormat.fromExtension(it))
        }
        assertEquals(BookFormat.CBR, BookFormat.fromExtension("cbr"))
        assertEquals(BookFormat.FB2, BookFormat.fromExtension("fb2"))
        assertEquals(BookFormat.MOBI, BookFormat.fromExtension("mobi"))
        assertEquals(BookFormat.MOBI, BookFormat.fromExtension("azw"))
        assertEquals(BookFormat.MOBI, BookFormat.fromExtension("AZW3"))
        assertEquals(BookFormat.MOBI, BookFormat.fromExtension("pdb"))
        assertEquals(BookFormat.MOBI, BookFormat.fromExtension("PRC"))
        assertEquals(BookFormat.RTF, BookFormat.fromExtension("rtf"))
        assertEquals(BookFormat.DOCX, BookFormat.fromExtension("docx"))
        assertEquals(BookFormat.ODT, BookFormat.fromExtension("odt"))
        assertEquals(BookFormat.ODT, BookFormat.fromExtension("FODT"))
    }

    @Test
    fun `maps filenames and only recognizes the fb2 zip compound suffix`() {
        assertEquals(BookFormat.FB2, BookFormat.fromFileName("book.fb2.zip"))
        assertEquals(BookFormat.FB2, BookFormat.fromFileName("BOOK.FB2.ZIP"))
        assertEquals(BookFormat.MOBI, BookFormat.fromFileName("book.pdb"))
        assertEquals(BookFormat.MOBI, BookFormat.fromFileName("book.prc"))
        assertEquals(BookFormat.MARKDOWN, BookFormat.fromFileName("chapter.qmd"))
        assertEquals(BookFormat.ODT, BookFormat.fromFileName("document.fodt"))
        assertNull(BookFormat.fromFileName("archive.zip"))
        assertNull(BookFormat.fromFileName("book.fb2.zipx"))
    }

    @Test
    fun `returns null for unknown extensions`() {
        assertNull(BookFormat.fromExtension("azw4"))
        assertNull(BookFormat.fromExtension("kfx"))
    }
}
