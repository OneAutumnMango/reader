package dev.reader.app.ui.search

import android.content.Context
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.reader.app.data.ChapterContent
import dev.reader.app.data.ChapterLoader
import dev.reader.core.model.Book
import dev.reader.core.model.BookFormat
import dev.reader.core.model.BookId
import dev.reader.core.model.Chapter
import dev.reader.core.reader.PdfRenderMode
import dev.reader.core.search.SearchMatch
import dev.reader.core.search.SearchOptions
import dev.reader.core.search.TextSearch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val MAX_TOTAL_HITS = 2000
private const val MAX_HITS_PER_CHAPTER = 100

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

private data class BookScan(val chapters: List<ChapterMatches>, val unreadable: Int)

/** Comics and page-image PDFs have no text to search. */
internal fun Book.hasSearchableText(): Boolean = when (format) {
    BookFormat.CBZ, BookFormat.CBR -> false
    BookFormat.PDF -> pdfRenderMode == PdfRenderMode.REFLOW_TEXT
    else -> true
}

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
     * Scans every searchable book's chapters for [regex], publishing results as each book finishes.
     * shortcut: parses chapters on demand rather than from an index, so a big library takes a while;
     * upgrade to filling the (currently unused) chapter_fts table at import time.
     */
    suspend fun searchLibraryText(
        context: Context,
        books: List<Book>,
        chaptersOf: (BookId) -> List<Chapter>,
        regex: Regex,
    ) {
        val searchable = books.filter { it.hasSearchableText() }
        searching = true
        booksSearched = 0
        booksToSearch = searchable.size
        results = TextSearchResults(emptyList(), 0)
        try {
            val found = mutableListOf<BookMatches>()
            var total = 0
            var unreadable = 0
            for (book in searchable) {
                if (total >= MAX_TOTAL_HITS) break
                val scan = searchBook(context, book, chaptersOf(book.id), regex, MAX_TOTAL_HITS - total)
                unreadable += scan.unreadable
                if (scan.chapters.isNotEmpty()) {
                    found += BookMatches(book, scan.chapters)
                    total += scan.chapters.sumOf { it.matches.size }
                }
                booksSearched++
                results = TextSearchResults(found.toList(), total, unreadableChapters = unreadable)
            }
            results = TextSearchResults(found.toList(), total, total >= MAX_TOTAL_HITS, unreadable)
            completedRequest = searchRequest
        } finally {
            searching = false
        }
    }

    private suspend fun searchBook(
        context: Context,
        book: Book,
        chapters: List<Chapter>,
        regex: Regex,
        budget: Int,
    ): BookScan {
        val found = mutableListOf<ChapterMatches>()
        var left = budget
        var unreadable = 0
        for (chapter in chapters) {
            if (left <= 0) break
            val document = (ChapterLoader.load(context, book, chapter) as? ChapterContent.Text)?.document
            if (document == null) unreadable++
            val matches = document?.let {
                withContext(Dispatchers.Default) { TextSearch.find(it, regex, minOf(MAX_HITS_PER_CHAPTER, left)) }
            }.orEmpty()
            if (matches.isNotEmpty()) {
                found += ChapterMatches(chapter, matches)
                left -= matches.size
            }
        }
        return BookScan(found, unreadable)
    }
}
