package dev.reader.app.ui.library

import dev.reader.core.model.Book
import dev.reader.core.model.BookFormat
import dev.reader.core.model.BookId
import dev.reader.data.store.LibrarySort
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class LibrarySortingTest {

    private fun book(id: String, title: String, author: String = "", added: Int = 1) = Book(
        BookId(id),
        title,
        author,
        format = BookFormat.EPUB,
        sourcePath = "/$id",
        addedAt = Instant.fromEpochSeconds(added.toLong()),
    )

    private val books = listOf(
        book("a", "beta", author = "Zed", added = 3),
        book("b", "Alpha", added = 1),
        book("c", "gamma", author = "Ann", added = 2),
    )
    private val lastRead = mapOf(
        BookId("a") to Instant.fromEpochSeconds(10),
        BookId("c") to Instant.fromEpochSeconds(20),
    )
    private val progress = mapOf(BookId("a") to 0.5f, BookId("b") to 0.1f)

    private fun sorted(sort: LibrarySort, descending: Boolean = false) =
        sortBooks(books, sort, descending, { lastRead[it] }, { progress[it] }).map { it.id.value }

    @Test
    fun `title sorts case-insensitively in either direction`() {
        assertEquals(listOf("b", "a", "c"), sorted(LibrarySort.TITLE))
        assertEquals(listOf("c", "a", "b"), sorted(LibrarySort.TITLE, descending = true))
    }

    @Test
    fun `books missing the sort value always go last`() {
        assertEquals(listOf("c", "a", "b"), sorted(LibrarySort.AUTHOR))
        assertEquals(listOf("c", "a", "b"), sorted(LibrarySort.LAST_READ, descending = true))
        assertEquals(listOf("a", "c", "b"), sorted(LibrarySort.LAST_READ))
        assertEquals(listOf("a", "b", "c"), sorted(LibrarySort.PROGRESS, descending = true))
    }

    @Test
    fun `date added newest first matches the old default`() {
        assertEquals(listOf("a", "c", "b"), sorted(LibrarySort.ADDED, descending = true))
    }
}
