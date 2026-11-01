package dev.reader.core.search

import dev.reader.core.model.Book
import dev.reader.core.model.BookFormat
import dev.reader.core.reader.PdfRenderMode

/** Comics and page-image PDFs have no text to search or index. */
fun Book.hasSearchableText(): Boolean = when (format) {
    BookFormat.CBZ, BookFormat.CBR -> false
    BookFormat.PDF -> pdfRenderMode == PdfRenderMode.REFLOW_TEXT
    else -> true
}
