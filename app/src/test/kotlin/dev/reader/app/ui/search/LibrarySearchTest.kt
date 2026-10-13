package dev.reader.app.ui.search

import dev.reader.core.model.Book
import dev.reader.core.model.BookFormat
import dev.reader.core.model.BookId
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class LibrarySearchTest {

    private fun book(id: String, title: String, author: String = "") = Book(
        id = BookId(id),
        title = title,
        author = author,
        format = BookFormat.TXT,
        sourcePath = "/books/$id.txt",
        addedAt = Instant.parse("2026-01-01T00:00:00Z"),
    )

    @Test
    fun `matches title case-insensitively`() {
        val books = listOf(book("1", "The Hobbit"), book("2", "Dune"))
        assertEquals(listOf(books[0]), filterBooksByQuery(books, "hobbit"))
    }

    @Test
    fun `matches author`() {
        val books = listOf(book("1", "The Hobbit", "Tolkien"), book("2", "Dune", "Herbert"))
        assertEquals(listOf(books[1]), filterBooksByQuery(books, "herbert"))
    }

    @Test
    fun `blank query returns everything`() {
        val books = listOf(book("1", "The Hobbit"), book("2", "Dune"))
        assertEquals(books, filterBooksByQuery(books, ""))
    }
}
