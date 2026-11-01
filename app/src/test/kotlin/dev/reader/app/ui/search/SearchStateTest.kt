package dev.reader.app.ui.search

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
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
import dev.reader.data.repo.SettingsRepository
import dev.reader.data.room.ReaderDatabase
import dev.reader.data.store.ReaderStores
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okio.FileSystem
import okio.Path.Companion.toPath
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
    private lateinit var settings: SettingsRepository
    private var scheduled = 0
    private var cancelled = 0
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
        settings = SettingsRepository(ReaderStores(FileSystem.SYSTEM, dir.path.toPath(), scope))
        indexer = LibraryIndexer(
            context,
            library,
            index,
            settings,
            scheduleWork = { scheduled++ },
            cancelWork = { cancelled++ },
        )
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
    fun `a book missing from the index is searched directly and flagged, never written to the index`() = runBlocking {
        val dragons = txt("dragons", "Once there was a Dragon.\n\nThe dragon slept.")
        val quiet = txt("quiet", "Nothing to see here.")
        val comic = txt("comic", "dragon", BookFormat.CBZ)

        val state = search(dragons, quiet, comic)

        assertEquals(listOf("dragons"), state.results!!.books.map { it.book.id.value })
        assertEquals(2, state.results!!.totalHits)
        assertEquals(2, state.results!!.unindexedBooks)
        assertEquals(emptyList(), index.page(0, 10))
        assertEquals(state.searchRequest, state.completedRequest)
        assertEquals(false, state.searching)
    }

    @Test
    fun `books kept out of the index are read directly without being called unindexed`() = runBlocking {
        val (book, chapters) = txt("secret", "The dragon slept.")
        index.replaceBook(book.id, listOf(chapters.single().id to "stale text with a dragon in it"))
        val state = SearchState()
        val regex = TextSearch.compile("dragon", SearchOptions())!!.getOrThrow()

        state.searchLibraryText(listOf(book), { chapters }, regex, index, indexer, useIndex = { false })

        assertEquals(1, state.results!!.totalHits)
        assertEquals(0, state.results!!.unindexedBooks)
        val snippet = state.results!!.books.single().chapters.single().matches.single().snippet
        assertEquals("The dragon slept.", snippet.text) // read from the file, not the stale indexed text
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
    fun `finds text inside an epub that is read directly`() = runBlocking {
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

    private suspend fun libraryOf(vararg books: Pair<Book, List<Chapter>>) =
        books.forEach { (book, chapters) -> library.upsertBook(book, chapters) }

    private suspend fun job() = settings.preferences.first().searchSettings.indexJob

    @Test
    fun `reindexing queues the books that belong in the index and works through them`() = runBlocking {
        val dragons = txt("dragons", "The dragon slept.")
        val kept = txt("kept", "A quiet book.")
        val excluded = txt("excluded", "Left out.")
        val comic = txt("comic", "x", BookFormat.CBZ)
        libraryOf(dragons, kept, excluded, comic)
        index.replaceBook(excluded.first.id, listOf(excluded.second.single().id to "stale"))
        index.replaceBook(comic.first.id, listOf(comic.second.single().id to "stale"))
        settings.update { it.copy(searchSettings = it.searchSettings.copy(indexExcludedBooks = setOf("excluded"))) }

        indexer.startReindex()

        assertEquals(1, scheduled)
        assertEquals(2, job().total)
        assertEquals(emptyList(), index.page(0, 10)) // stale rows of left-out and unsearchable books are gone
        indexer.runQueue()

        val stored = index.page(0, 10)
        assertEquals(setOf("dragons", "kept"), stored.map { it.bookId.value }.toSet())
        assertTrue(job().finished)
        assertEquals(0, job().pending.size)
    }

    @Test
    fun `a paused reindex does nothing until resumed, and picks up where it left off`() = runBlocking {
        val first = txt("first", "Alpha.")
        val second = txt("second", "Beta.")
        libraryOf(first, second)
        indexer.startReindex()

        indexer.pause()
        assertEquals(1, cancelled)
        indexer.runQueue()
        assertEquals(emptyList(), index.page(0, 10))
        assertTrue(job().isPaused)
        assertEquals(2, job().pending.size)

        indexer.resume()
        assertEquals(2, scheduled)
        var progress = 0
        indexer.runQueue { progress = it.done }

        assertEquals(2, index.page(0, 10).size)
        assertEquals(2, progress)
        assertTrue(job().finished)
    }

    @Test
    fun `a reindex interrupted partway continues from the books still pending`() = runBlocking {
        val first = txt("first", "Alpha.")
        val second = txt("second", "Beta.")
        libraryOf(first, second)
        indexer.startReindex()
        val firstId = job().pending.first()

        // Stop after the first book, as if the app were killed.
        var seen = 0
        runCatching { indexer.runQueue { if (++seen == 2) throw kotlinx.coroutines.CancellationException("stop") } }

        assertEquals(listOf(firstId), index.page(0, 10).map { it.bookId.value })
        assertEquals(1, job().pending.size)
        indexer.runQueue()
        assertEquals(2, index.page(0, 10).size)
    }

    @Test
    fun `excluding a book removes it from the index and from a reindex under way`() = runBlocking {
        val first = txt("first", "Alpha.")
        val second = txt("second", "Beta.")
        libraryOf(first, second)
        indexer.startReindex()
        index.replaceBook(second.first.id, listOf(second.second.single().id to "Beta."))

        indexer.forget(second.first.id)

        assertEquals(listOf("first"), job().pending)
        assertEquals(emptyList(), index.page(0, 10))
    }

    @Test
    fun `nothing is queued while the index is switched off, and unfinished work is rescheduled`() = runBlocking {
        libraryOf(txt("first", "Alpha."))
        settings.update { it.copy(searchSettings = it.searchSettings.copy(indexEnabled = false)) }

        indexer.startReindex()
        assertEquals(0, scheduled)
        assertEquals(0, job().total)

        settings.update { it.copy(searchSettings = it.searchSettings.copy(indexEnabled = true)) }
        indexer.ensureScheduled()
        assertEquals(0, scheduled)
        indexer.startReindex()
        indexer.ensureScheduled()
        assertEquals(2, scheduled)
    }

    @Test
    fun `clearing the index empties it and resets the job`() = runBlocking {
        val first = txt("first", "Alpha.")
        libraryOf(first)
        indexer.startReindex()
        indexer.runQueue()

        indexer.clearIndex()

        assertEquals(emptyList(), index.page(0, 10))
        assertEquals(0, job().total)
    }
}
