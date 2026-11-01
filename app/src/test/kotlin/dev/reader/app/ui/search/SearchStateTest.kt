package dev.reader.app.ui.search

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.reader.app.data.IndexStatus
import dev.reader.app.data.LibraryIndexer
import dev.reader.core.model.Book
import dev.reader.core.model.BookFormat
import dev.reader.core.model.BookId
import dev.reader.core.model.Chapter
import dev.reader.core.model.ChapterId
import dev.reader.core.search.SearchOptions
import dev.reader.core.search.TextSearch
import dev.reader.data.repo.LibraryRepository
import dev.reader.data.repo.SearchRepository
import dev.reader.data.room.ReaderDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

@RunWith(RobolectricTestRunner::class)
class SearchStateTest {

    private lateinit var database: ReaderDatabase
    private lateinit var library: LibraryRepository
    private lateinit var index: SearchRepository
    private lateinit var indexer: LibraryIndexer
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val dir = File.createTempFile("search-state", "").apply {
        delete()
        mkdirs()
    }

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        database = Room.inMemoryDatabaseBuilder(context, ReaderDatabase::class.java).allowMainThreadQueries().build()
        library = LibraryRepository(database)
        index = SearchRepository(database)
        indexer = LibraryIndexer(context, library, index, scope)
    }

    @After
    fun tearDown() {
        database.close()
        dir.deleteRecursively()
    }

    private fun txt(id: String, text: String, format: BookFormat = BookFormat.TXT): Pair<Book, List<Chapter>> {
        val file = File(dir, "$id.txt").apply { writeText(text) }
        val book = Book(BookId(id), id, format = format, sourcePath = file.path, addedAt = Instant.fromEpochSeconds(0))
        return book to listOf(Chapter(ChapterId("$id-1"), book.id, "One", file.path, 0))
    }

    private suspend fun search(
        vararg books: Pair<Book, List<Chapter>>,
        query: String = "dragon",
        options: SearchOptions = SearchOptions(),
    ): SearchState {
        val state = SearchState()
        val chapters = books.associate { it.first.id to it.second }
        val regex = TextSearch.compile(query, options)!!.getOrThrow()
        state.searchLibraryText(books.map { it.first }, { chapters.getValue(it) }, regex, index, indexer)
        return state
    }

    @Test
    fun `searches the stored text without opening the book again`() = runBlocking {
        val (book, chapters) = txt("dragons", "Nothing here.")
        index.replaceBook(book.id, listOf(chapters.single().id to "A quiet start\nThe dragon slept"))
        File(book.sourcePath).delete()

        val state = search(book to chapters)

        val hit = state.results!!.books.single().chapters.single().matches.single()
        assertEquals(1, hit.block)
        assertEquals(1, state.results!!.totalHits)
    }

    @Test
    fun `a book missing from the index is read once and indexed`() = runBlocking {
        val dragons = txt("dragons", "Once there was a Dragon.\n\nThe dragon slept.")
        val quiet = txt("quiet", "Nothing to see here.")
        val comic = txt("comic", "dragon", BookFormat.CBZ)

        val state = search(dragons, quiet, comic)

        assertEquals(listOf("dragons"), state.results!!.books.map { it.book.id.value })
        assertEquals(2, state.results!!.totalHits)
        assertEquals(listOf("dragons-1", "quiet-1"), index.page(0, 10).map { it.chapterId.value }.sorted())
        assertEquals(state.searchRequest, state.completedRequest)
        assertEquals(false, state.searching)
    }

    @Test
    fun `search options change what matches`() = runBlocking {
        val book = txt("dragons", "A Dragon and a dragon.")

        suspend fun hits(query: String, options: SearchOptions) = search(book, query = query, options = options)
            .results!!.totalHits

        assertEquals(2, hits("dragon", SearchOptions()))
        assertEquals(1, hits("dragon", SearchOptions(matchCase = true)))
        assertEquals(2, hits("[Dd]rag.n", SearchOptions(regex = true)))
        assertEquals(2, hits("a  dragon", SearchOptions(anyWhitespace = true)))
        assertEquals(1, hits("a  dragon", SearchOptions(anyWhitespace = true, matchCase = true)))
    }

    @Test
    fun `finds text inside an epub that is not indexed yet`() = runBlocking {
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

        val state = search(book to chapters)

        assertEquals(1, state.results!!.totalHits)
        assertEquals(2, index.page(0, 10).size)
    }

    @Test
    fun `a search only runs when submitted, and counts chapters it could not read`() = runBlocking {
        val readable = txt("readable", "A dragon.")
        val broken = Book(
            BookId("broken"),
            "Broken",
            format = BookFormat.TXT,
            sourcePath = "/missing.txt",
            addedAt = Instant.fromEpochSeconds(0),
        ) to listOf(Chapter(ChapterId("broken-1"), BookId("broken"), "One", "/missing.txt", 0))
        val state = SearchState()
        state.query = "  dragon "
        assertEquals(0, state.searchRequest)

        state.submit()

        assertEquals(1, state.searchRequest)
        assertEquals("dragon", state.submittedQuery)
        assertTrue(state.completedRequest < state.searchRequest)
        val chapters = mapOf(readable.first.id to readable.second, broken.first.id to broken.second)
        val regex = TextSearch.compile(state.submittedQuery, state.submittedOptions)!!.getOrThrow()
        state.searchLibraryText(listOf(readable.first, broken.first), { chapters.getValue(it) }, regex, index, indexer)
        assertEquals(state.searchRequest, state.completedRequest)
        assertEquals(1, state.results!!.totalHits)
        assertEquals(1, state.results!!.unreadableChapters)
    }

    @Test
    fun `reindexing the library rebuilds the stored text of searchable books`() = runBlocking {
        val (book, chapters) = txt("dragons", "The dragon slept.")
        library.upsertBook(book, chapters)
        index.replaceBook(book.id, listOf(chapters.single().id to "stale text"))
        val (comic, comicChapters) = txt("comic", "x", BookFormat.CBZ)
        library.upsertBook(comic, comicChapters)
        index.replaceBook(comic.id, listOf(comicChapters.single().id to "should be dropped"))

        indexer.reindexLibrary()
        while (indexer.status.first { it !is IndexStatus.Idle } is IndexStatus.Running) kotlinx.coroutines.yield()

        assertEquals(IndexStatus.Done(books = 1, unreadableChapters = 0), indexer.status.value)
        val stored = index.page(0, 10)
        assertEquals(listOf("dragons-1"), stored.map { it.chapterId.value })
        assertEquals("The dragon slept.", stored.single().body)
    }
}
