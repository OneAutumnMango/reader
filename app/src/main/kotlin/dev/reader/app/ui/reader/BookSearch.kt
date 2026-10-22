package dev.reader.app.ui.reader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import dev.reader.app.data.ChapterContent
import dev.reader.app.data.ChapterLoader
import dev.reader.core.model.Book
import dev.reader.core.model.Chapter
import dev.reader.core.model.ChapterId
import dev.reader.core.richtext.RichDocument
import dev.reader.core.search.SearchOptions
import dev.reader.core.search.TextSearch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private const val MAX_HITS = 1000
private const val TYPING_DEBOUNCE_MILLIS = 300L

/** A request to bring block [block] into view; [id] makes repeated jumps to the same block distinct. */
data class BlockJump(val block: Int, val id: Int)

/** Book-wide search state for the reader: the query, its matches, and which match to show. */
class BookSearch {
    var open by mutableStateOf(false)
    var query by mutableStateOf("")
    var options by mutableStateOf(SearchOptions())
    var hits by mutableStateOf<List<SearchHit>?>(null)
        internal set
    var searching by mutableStateOf(false)
        internal set
    var active by mutableStateOf<Int?>(null)
        private set
    private var jump by mutableStateOf<Pair<Int, BlockJump>?>(null)

    /** Stable between edits, so it can key effects; null while closed or empty. */
    val compiled by derivedStateOf { if (open) TextSearch.compile(query, options) else null }

    val highlight: Regex? get() = compiled?.getOrNull()

    val ui: SearchUiState
        get() = SearchUiState(query, options, hits, searching, compiled?.isFailure == true, active)

    /**
     * Makes match [index] current and returns the chapter page it lives on. [nonce] is caller-
     * supplied (rather than generated here) so it can share one nonce space with other jump
     * sources — e.g. the reader scrubber — letting whichever jump is newest always win.
     */
    fun select(index: Int, nonce: Int): Int? {
        val hit = hits?.getOrNull(index) ?: return null
        active = index
        jump = hit.chapterIndex to BlockJump(hit.match.block, nonce)
        return hit.chapterIndex
    }

    fun jumpFor(chapterPage: Int): BlockJump? = jump?.takeIf { it.first == chapterPage }?.second

    internal fun reset() {
        hits = null
        active = null
    }
}

/** Runs [BookSearch] queries over every text chapter of [book], loading (and caching) each as needed. */
@Composable
fun rememberBookSearch(book: Book, chapters: List<Chapter>): BookSearch {
    val context = LocalContext.current
    val search = remember(book.id) { BookSearch() }
    val documents = remember(book.id, book.pdfRenderMode, book.chapterPattern, book.chapterExcludedLines) {
        mutableMapOf<ChapterId, RichDocument>()
    }
    LaunchedEffect(search.compiled, chapters, documents) {
        search.reset()
        val regex = search.compiled?.getOrNull() ?: return@LaunchedEffect
        delay(TYPING_DEBOUNCE_MILLIS)
        search.searching = true
        try {
            val found = mutableListOf<SearchHit>()
            for ((index, chapter) in chapters.withIndex()) {
                if (found.size >= MAX_HITS) break
                val document = documents[chapter.id]
                    ?: (ChapterLoader.load(context, book, chapter) as? ChapterContent.Text)?.document
                        ?.also { documents[chapter.id] = it }
                if (document != null) {
                    val matches = withContext(Dispatchers.Default) {
                        TextSearch.find(document, regex, MAX_HITS - found.size)
                    }
                    found += matches.map { SearchHit(index, chapter.title, it) }
                    search.hits = found.toList()
                }
            }
            search.hits = found.toList()
        } finally {
            search.searching = false
        }
    }
    return search
}
