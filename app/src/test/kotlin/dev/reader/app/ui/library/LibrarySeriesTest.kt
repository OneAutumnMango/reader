package dev.reader.app.ui.library

import dev.reader.core.model.Book
import dev.reader.core.model.BookFormat
import dev.reader.core.model.BookId
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class LibrarySeriesTest {

    private fun book(id: String, series: String? = null, title: String = id) = Book(
        BookId(id),
        title,
        format = BookFormat.EPUB,
        sourcePath = "/$id",
        addedAt = Instant.fromEpochSeconds(1),
        series = series,
    )

    @Test
    fun `books without a series stay singles in order`() {
        val a = book("a")
        val b = book("b")
        assertEquals(listOf(LibraryItem.Single(a), LibraryItem.Single(b)), groupBySeries(listOf(a, b)))
    }

    @Test
    fun `two books sharing a series become one Series placed where its first book was`() {
        val a = book("a")
        val s1 = book("s1", "S")
        val b = book("b")
        val s2 = book("s2", "S")
        assertEquals(
            listOf(LibraryItem.Single(a), LibraryItem.Series("S", listOf(s1, s2)), LibraryItem.Single(b)),
            groupBySeries(listOf(a, s1, b, s2)),
        )
    }

    @Test
    fun `a series with one book renders as a single`() {
        val s1 = book("s1", "S")
        assertEquals(listOf(LibraryItem.Single(s1)), groupBySeries(listOf(s1)))
    }

    @Test
    fun `books in a series are natural sorted`() {
        val b10 = book("x", "S", "Book 10")
        val b2 = book("y", "S", "Book 2")
        assertEquals(
            listOf(LibraryItem.Series("S", listOf(b2, b10))),
            groupBySeries(listOf(b10, b2)),
        )
    }

    @Test
    fun `blank series name is treated as no series`() {
        val a = book("a", " ")
        val b = book("b", " ")
        assertEquals(listOf(LibraryItem.Single(a), LibraryItem.Single(b)), groupBySeries(listOf(a, b)))
    }

    @Test
    fun `same name from different folders merges`() {
        val a = book("a", "S")
        val b = book("b", "S")
        assertEquals(listOf(LibraryItem.Series("S", listOf(a, b))), groupBySeries(listOf(a, b)))
    }
}
