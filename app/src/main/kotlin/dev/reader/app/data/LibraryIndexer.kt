package dev.reader.app.data

import android.content.Context
import dev.reader.core.model.Book
import dev.reader.core.model.Chapter
import dev.reader.core.model.ChapterId
import dev.reader.core.search.hasSearchableText
import dev.reader.core.search.indexText
import dev.reader.data.repo.LibraryRepository
import dev.reader.data.repo.SearchRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

sealed interface IndexStatus {
    data object Idle : IndexStatus
    data class Running(val booksDone: Int, val booksTotal: Int) : IndexStatus
    data class Done(val books: Int, val unreadableChapters: Int) : IndexStatus
}

/** Builds the library search index from the same chapter content the reader shows, so block numbers line up. */
class LibraryIndexer(
    private val context: Context,
    private val library: LibraryRepository,
    private val search: SearchRepository,
    private val scope: CoroutineScope,
) {
    private val _status = MutableStateFlow<IndexStatus>(IndexStatus.Idle)
    val status: StateFlow<IndexStatus> = _status.asStateFlow()
    private var job: Job? = null

    /** Loads [chapters]' text for the index; chapters that can't be read are counted, not stored. */
    suspend fun loadTexts(book: Book, chapters: List<Chapter>): Pair<List<Pair<ChapterId, String>>, Int> {
        val texts = mutableListOf<Pair<ChapterId, String>>()
        var unreadable = 0
        for (chapter in chapters) {
            val document = (ChapterLoader.load(context, book, chapter) as? ChapterContent.Text)?.document
            if (document == null) unreadable++ else texts += chapter.id to document.indexText()
        }
        return texts to unreadable
    }

    /** Re-reads every searchable book into the index, book by book, so search keeps working meanwhile. */
    fun reindexLibrary() {
        if (job?.isActive == true) return
        job = scope.launch(Dispatchers.IO) {
            val books = library.observeBooks().first()
            val searchable = books.filter { it.hasSearchableText() }
            var unreadable = 0
            _status.value = IndexStatus.Running(0, searchable.size)
            books.filterNot { it.hasSearchableText() }.forEach { search.removeBook(it.id) }
            searchable.forEachIndexed { done, book ->
                val (texts, failed) = loadTexts(book, library.chapters(book.id))
                search.replaceBook(book.id, texts)
                unreadable += failed
                _status.value = IndexStatus.Running(done + 1, searchable.size)
            }
            _status.value = IndexStatus.Done(searchable.size, unreadable)
        }
    }
}
