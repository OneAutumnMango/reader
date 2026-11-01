package dev.reader.app.ui.search

import dev.reader.core.model.Book
import dev.reader.core.model.BookFormat
import dev.reader.core.model.BookId
import dev.reader.core.model.Chapter
import dev.reader.core.model.ChapterId
import dev.reader.core.search.SearchOptions
import dev.reader.core.search.TextSearch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

@RunWith(RobolectricTestRunner::class)
class SearchStateTest {

    private val dir = File.createTempFile("search-state", "").apply {
        delete()
        mkdirs()
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun book(id: String, text: String, format: BookFormat = BookFormat.TXT): Book {
        val file = File(dir, "$id.txt").apply { writeText(text) }
        return Book(BookId(id), id, format = format, sourcePath = file.path, addedAt = Instant.fromEpochSeconds(0))
    }

    @Test
    fun `finds matches across books, skipping books with none and page-based formats`() = runBlocking {
        val dragons = book("dragons", "Once there was a Dragon.\n\nThe dragon slept.")
        val quiet = book("quiet", "Nothing to see here.")
        val comic = book("comic", "dragon", BookFormat.CBZ)
        val chapters = listOf(dragons, quiet, comic).associate {
            it.id to listOf(Chapter(ChapterId("${it.id.value}-1"), it.id, "One", it.sourcePath, 0))
        }
        val state = SearchState()
        val regex = TextSearch.compile("dragon", SearchOptions())!!.getOrThrow()

        state.searchLibraryText(
            RuntimeEnvironment.getApplication(),
            listOf(dragons, quiet, comic),
            { chapters.getValue(it) },
            regex,
            "dragon",
        )

        val results = state.results!!
        assertEquals(listOf("dragons"), results.books.map { it.book.id.value })
        assertEquals(2, results.totalHits)
        assertEquals("dragon", state.resultsKey)
        assertFalse(state.searching)
        assertTrue(comic.hasSearchableText().not())
    }
}
