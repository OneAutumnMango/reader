package dev.reader.app.ui.data

import dev.reader.core.model.Book
import dev.reader.core.model.BookFormat
import dev.reader.core.model.BookId
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class RestorePreviewTest {

    private fun book(id: String) =
        Book(BookId(id), id, format = BookFormat.TXT, sourcePath = "/$id", addedAt = Instant.fromEpochMilliseconds(0))

    @Test
    fun `books are grouped by whether they're new, shared, or only in the current library`() {
        val current = listOf(book("kept"), book("shared"))
        val backup = listOf(book("shared"), book("new"))

        val preview = restorePreview(current, backup)

        assertEquals(listOf(book("new")), preview.added)
        assertEquals(listOf(book("shared")), preview.updated)
        assertEquals(listOf(book("kept")), preview.onlyInLibrary)
    }

    @Test
    fun `an empty backup only reports every current book as library-only`() {
        val current = listOf(book("a"), book("b"))

        val preview = restorePreview(current, emptyList())

        assertEquals(emptyList(), preview.added)
        assertEquals(emptyList(), preview.updated)
        assertEquals(current, preview.onlyInLibrary)
    }
}
