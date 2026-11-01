package dev.reader.app.ui.search

import dev.reader.core.model.Book
import dev.reader.core.model.BookFormat
import dev.reader.core.model.BookId
import dev.reader.core.model.Chapter
import dev.reader.core.model.ChapterId
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

    @Test
    fun `chapter titles match case-insensitively in book then chapter order`() {
        val books = listOf(book("1", "A"), book("2", "B"))
        val chapters = listOf(
            Chapter(ChapterId("b-2"), BookId("2"), "The Dragon Returns", "", 2),
            Chapter(ChapterId("a-1"), BookId("1"), "dragon egg", "", 1),
            Chapter(ChapterId("b-1"), BookId("2"), "Dragon Lair", "", 1),
            Chapter(ChapterId("a-0"), BookId("1"), "Prologue", "", 0),
        )

        val matches = matchChaptersByTitle(books, chapters, "DRAGON")

        assertEquals(listOf("a-1", "b-1", "b-2"), matches.map { it.chapter.id.value })
        assertEquals(emptyList(), matchChaptersByTitle(books, chapters, " "))
    }
}
