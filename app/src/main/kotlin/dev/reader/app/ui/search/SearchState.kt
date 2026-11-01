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
import dev.reader.core.search.TextSearch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val MAX_TOTAL_HITS = 2000
private const val MAX_HITS_PER_CHAPTER = 100

data class ChapterMatches(val chapter: Chapter, val matches: List<SearchMatch>)

data class BookMatches(val book: Book, val chapters: List<ChapterMatches>) {
    val total: Int get() = chapters.sumOf { it.matches.size }
}

data class TextSearchResults(val books: List<BookMatches>, val totalHits: Int, val truncated: Boolean = false)

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
    var results by mutableStateOf<TextSearchResults?>(null)
        private set

    /** The query [results] were computed for, so coming back to an unchanged query doesn't search again. */
    var resultsKey by mutableStateOf<String?>(null)
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

    fun clearResults() {
        results = null
        resultsKey = null
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
        key: String,
    ) {
        val searchable = books.filter { it.hasSearchableText() }
        searching = true
        booksSearched = 0
        booksToSearch = searchable.size
        results = TextSearchResults(emptyList(), 0)
        try {
            val found = mutableListOf<BookMatches>()
            var total = 0
            for (book in searchable) {
                if (total >= MAX_TOTAL_HITS) break
                val chapters = searchBook(context, book, chaptersOf(book.id), regex, MAX_TOTAL_HITS - total)
                if (chapters.isNotEmpty()) {
                    found += BookMatches(book, chapters)
                    total += chapters.sumOf { it.matches.size }
                    results = TextSearchResults(found.toList(), total)
                }
                booksSearched++
            }
            results = TextSearchResults(found.toList(), total, truncated = total >= MAX_TOTAL_HITS)
            resultsKey = key
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
    ): List<ChapterMatches> {
        val found = mutableListOf<ChapterMatches>()
        var left = budget
        for (chapter in chapters) {
            if (left <= 0) break
            val document = (ChapterLoader.load(context, book, chapter) as? ChapterContent.Text)?.document
            val matches = document?.let {
                withContext(Dispatchers.Default) { TextSearch.find(it, regex, minOf(MAX_HITS_PER_CHAPTER, left)) }
            }.orEmpty()
            if (matches.isNotEmpty()) {
                found += ChapterMatches(chapter, matches)
                left -= matches.size
            }
        }
        return found
    }
}
