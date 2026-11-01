package dev.reader.app.ui.search

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.reader.app.data.LibraryIndexer
import dev.reader.core.model.Book
import dev.reader.core.model.BookId
import dev.reader.core.model.Chapter
import dev.reader.core.model.ChapterId
import dev.reader.core.search.SearchMatch
import dev.reader.core.search.SearchOptions
import dev.reader.core.search.TextSearch
import dev.reader.core.search.hasSearchableText
import dev.reader.data.repo.IndexedChapter
import dev.reader.data.repo.SearchRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val MAX_TOTAL_HITS = 2000
private const val MAX_HITS_PER_CHAPTER = 100
private const val INDEX_PAGE_SIZE = 25

data class ChapterMatches(val chapter: Chapter, val matches: List<SearchMatch>)

data class BookMatches(val book: Book, val chapters: List<ChapterMatches>) {
    val total: Int get() = chapters.sumOf { it.matches.size }
}

data class TextSearchResults(
    val books: List<BookMatches>,
    val totalHits: Int,
    val truncated: Boolean = false,
    /** Chapters that failed to load, so a search that quietly found nothing can be told from one that couldn't read. */
    val unreadableChapters: Int = 0,
)

/**
 * Library search state, kept above the search screen so the query, results and scroll positions
 * are still there when you come back from reading a result.
 */
class SearchState {
    var query by mutableStateOf("")
    var inText by mutableStateOf(false)
    var options by mutableStateOf(SearchOptions())
    var results by mutableStateOf<TextSearchResults?>(null)
        private set

    /** Bumped by [submit]; the search only runs for an explicit request, never as the query is typed. */
    var searchRequest by mutableIntStateOf(0)
        private set

    /** The last request whose search ran to the end, so coming back from reading a result doesn't search again. */
    var completedRequest by mutableIntStateOf(0)
        private set

    /** What the shown [results] were searched with, which may differ from what's typed now. */
    var submittedQuery by mutableStateOf("")
        private set
    var submittedOptions by mutableStateOf(SearchOptions())
        private set
    var searching by mutableStateOf(false)
        private set
    var booksSearched by mutableIntStateOf(0)
        private set
    var booksToSearch by mutableIntStateOf(0)
        private set
    var collapsed by mutableStateOf<Set<BookId>>(emptySet())
    val gridState = LazyGridState()
    val listState = LazyListState()

    fun submit() {
        submittedQuery = query.trim()
        submittedOptions = options
        searchRequest++
    }

    fun clearResults() {
        results = null
        completedRequest = searchRequest
    }

    /**
     * Searches the stored text of every searchable book for [regex], publishing results as it goes.
     * Books missing from the index are read and indexed on the spot, so that only happens once.
     */
    suspend fun searchLibraryText(
        books: List<Book>,
        chaptersOf: (BookId) -> List<Chapter>,
        regex: Regex,
        index: SearchRepository,
        indexer: LibraryIndexer,
    ) {
        val searchable = books.filter { it.hasSearchableText() }
        searching = true
        booksSearched = 0
        booksToSearch = searchable.size
        results = TextSearchResults(emptyList(), 0)
        try {
            val scan = Scan(searchable, searchable.flatMap { chaptersOf(it.id) }.associateBy { it.id }, regex)
            scanIndex(scan, index)
            for (book in searchable.filter { it.id !in scan.indexed }) {
                if (scan.total >= MAX_TOTAL_HITS) break
                val (texts, failed) = indexer.loadTexts(book, chaptersOf(book.id))
                index.replaceBook(book.id, texts)
                scan.unreadable += failed
                texts.forEach { (chapterId, text) -> scan.add(book.id, chapterId, text) }
                scan.indexed += book.id
                booksSearched = scan.indexed.size
                publish(scan)
            }
            results = scan.results(truncated = scan.total >= MAX_TOTAL_HITS)
            completedRequest = searchRequest
        } finally {
            searching = false
        }
    }

    private suspend fun scanIndex(scan: Scan, index: SearchRepository) {
        var after = 0
        while (scan.total < MAX_TOTAL_HITS) {
            val rows = index.page(after, INDEX_PAGE_SIZE)
            if (rows.isEmpty()) break
            after = rows.last().rowId
            withContext(Dispatchers.Default) { rows.forEach { scan.add(it) } }
            booksSearched = scan.indexed.size
            publish(scan)
        }
    }

    private fun publish(scan: Scan) {
        results = scan.results(truncated = false)
    }

    /** Hits gathered so far; only touched by one search at a time. */
    private class Scan(books: List<Book>, val chapters: Map<ChapterId, Chapter>, val regex: Regex) {
        private val order = books.withIndex().associate { (position, book) -> book.id to position }
        private val byId = books.associateBy { it.id }
        private val found = mutableMapOf<BookId, MutableList<ChapterMatches>>()
        val indexed = mutableSetOf<BookId>()
        var unreadable = 0
        var total = 0
            private set

        fun add(row: IndexedChapter) = add(row.bookId, row.chapterId, row.body)

        fun add(bookId: BookId, chapterId: ChapterId, body: String) {
            if (bookId !in byId) return
            indexed += bookId
            val chapter = chapters[chapterId] ?: return
            val matches = TextSearch.findInIndexedText(body, regex, minOf(MAX_HITS_PER_CHAPTER, MAX_TOTAL_HITS - total))
            if (matches.isEmpty()) return
            found.getOrPut(bookId) { mutableListOf() } += ChapterMatches(chapter, matches)
            total += matches.size
        }

        fun results(truncated: Boolean) = TextSearchResults(
            found.entries.sortedBy { order.getValue(it.key) }.map { (id, chapterMatches) ->
                BookMatches(byId.getValue(id), chapterMatches.sortedBy { it.chapter.orderIndex })
            },
            total,
            truncated,
            unreadable,
        )
    }
}
