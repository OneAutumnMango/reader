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
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
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
        )

        val results = state.results!!
        assertEquals(listOf("dragons"), results.books.map { it.book.id.value })
        assertEquals(2, results.totalHits)
        assertFalse(state.searching)
        assertTrue(comic.hasSearchableText().not())
    }

    @Test
    fun `search options change what matches`() = runBlocking {
        val dragons = book("dragons", "A Dragon and a dragon.")
        val chapters = mapOf(dragons.id to listOf(Chapter(ChapterId("c"), dragons.id, "One", dragons.sourcePath, 0)))

        suspend fun hits(query: String, options: SearchOptions): Int {
            val state = SearchState()
            val regex = TextSearch.compile(query, options)!!.getOrThrow()
            val context = RuntimeEnvironment.getApplication()
            state.searchLibraryText(context, listOf(dragons), { chapters.getValue(it) }, regex)
            return state.results!!.totalHits
        }

        assertEquals(2, hits("dragon", SearchOptions()))
        assertEquals(1, hits("dragon", SearchOptions(matchCase = true)))
        assertEquals(2, hits("[Dd]rag.n", SearchOptions(regex = true)))
        assertEquals(2, hits("a  dragon", SearchOptions(anyWhitespace = true)))
        assertEquals(1, hits("a  dragon", SearchOptions(anyWhitespace = true, matchCase = true)))
    }

    @Test
    fun `finds text inside an epub`() = runBlocking {
        val file = File(dir, "story.epub")
        ZipOutputStream(file.outputStream()).use { zip ->
            fun entry(name: String, content: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
            entry(
                "META-INF/container.xml",
                """<?xml version="1.0"?><container><rootfiles>
                <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
                </rootfiles></container>""",
            )
            entry(
                "OEBPS/content.opf",
                """<?xml version="1.0"?><package><metadata><title>Story</title></metadata>
                <manifest><item id="c1" href="c1.xhtml" media-type="application/xhtml+xml"/>
                <item id="c2" href="c2.xhtml" media-type="application/xhtml+xml"/></manifest>
                <spine><itemref idref="c1"/><itemref idref="c2"/></spine></package>""",
            )
            entry("OEBPS/c1.xhtml", "<html><body><h1>One</h1><p>A quiet start.</p></body></html>")
            entry("OEBPS/c2.xhtml", "<html><body><h1>Two</h1><p>Then a dragon appeared.</p></body></html>")
        }
        val book = Book(
            BookId("epub"),
            "Story",
            format = BookFormat.EPUB,
            sourcePath = file.path,
            addedAt = Instant.fromEpochSeconds(0),
        )
        val chapters = listOf(
            Chapter(ChapterId("c1"), book.id, "One", file.path, 0),
            Chapter(ChapterId("c2"), book.id, "Two", file.path, 1),
        )
        val state = SearchState()
        val regex = TextSearch.compile("dragon", SearchOptions())!!.getOrThrow()

        state.searchLibraryText(RuntimeEnvironment.getApplication(), listOf(book), { chapters }, regex)

        assertEquals(1, state.results!!.totalHits)
    }

    @Test
    fun `a search only runs when submitted, and counts chapters it could not read`() = runBlocking {
        val readable = book("readable", "A dragon.")
        val broken = Book(
            BookId("broken"),
            "Broken",
            format = BookFormat.TXT,
            sourcePath = "/missing.txt",
            addedAt = Instant.fromEpochSeconds(0),
        )
        val chapters = listOf(readable, broken).associate {
            it.id to listOf(Chapter(ChapterId("${it.id.value}-1"), it.id, "One", it.sourcePath, 0))
        }
        val state = SearchState()
        state.query = "  dragon "
        assertEquals(0, state.searchRequest)

        state.submit()

        assertEquals(1, state.searchRequest)
        assertEquals("dragon", state.submittedQuery)
        assertTrue(state.completedRequest < state.searchRequest)
        val regex = TextSearch.compile(state.submittedQuery, state.submittedOptions)!!.getOrThrow()
        val context = RuntimeEnvironment.getApplication()
        state.searchLibraryText(context, listOf(readable, broken), { chapters.getValue(it) }, regex)
        assertEquals(state.searchRequest, state.completedRequest)
        assertEquals(1, state.results!!.totalHits)
        assertEquals(1, state.results!!.unreadableChapters)
    }
}
