package dev.reader.core.search

import dev.reader.core.model.Book
import dev.reader.core.model.BookFormat
import dev.reader.core.model.BookId
import dev.reader.core.reader.PdfRenderMode
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class SearchableTest {

    private fun book(format: BookFormat, pdfRenderMode: PdfRenderMode? = null) = Book(
        BookId("b"),
        "B",
        format = format,
        sourcePath = "/b",
        pdfRenderMode = pdfRenderMode,
        addedAt = Instant.fromEpochSeconds(0),
    )

    @Test
    fun `only formats with real text are searchable`() {
        assertEquals(true, book(BookFormat.EPUB).hasSearchableText())
        assertEquals(true, book(BookFormat.TXT).hasSearchableText())
        assertEquals(false, book(BookFormat.CBZ).hasSearchableText())
        assertEquals(false, book(BookFormat.CBR).hasSearchableText())
        assertEquals(false, book(BookFormat.PDF).hasSearchableText())
        assertEquals(false, book(BookFormat.PDF, PdfRenderMode.FIXED_PAGE).hasSearchableText())
        assertEquals(true, book(BookFormat.PDF, PdfRenderMode.REFLOW_TEXT).hasSearchableText())
    }
}
