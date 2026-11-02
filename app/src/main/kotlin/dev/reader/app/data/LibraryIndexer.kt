package dev.reader.app.data

import android.content.Context
import dev.reader.core.model.Book
import dev.reader.core.model.BookId
import dev.reader.core.model.Chapter
import dev.reader.core.model.ChapterId
import dev.reader.core.search.hasSearchableText
import dev.reader.core.search.indexText
import dev.reader.data.repo.LibraryRepository
import dev.reader.data.repo.SearchRepository
import dev.reader.data.repo.SettingsRepository
import dev.reader.data.store.SearchIndexJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first

/**
 * Builds the library search index from the same chapter content the reader shows, so block numbers
 * line up. A reindex is a queue kept in the settings, so it can be paused, and carries on after the
 * app is closed: [scheduleWork] asks the system to call [runQueue], and [cancelWork] stops that.
 */
class LibraryIndexer(
    private val context: Context,
    private val library: LibraryRepository,
    private val search: SearchRepository,
    private val settings: SettingsRepository,
    private val scheduleWork: () -> Unit = {},
    private val cancelWork: () -> Unit = {},
) {

    /** Loads [chapters]' text for the index; chapters that can't be read are counted, not stored. */
    suspend fun loadTexts(book: Book, chapters: List<Chapter>): Pair<List<Pair<ChapterId, String>>, Int> {
        val texts = mutableListOf<Pair<ChapterId, String>>()
        var unreadable = 0
        for (chapter in chapters) {
            currentCoroutineContext().ensureActive()
            val document = (ChapterLoader.load(context, book, chapter) as? ChapterContent.Text)?.document
            if (document == null) unreadable++ else texts += chapter.id to document.indexText()
        }
        return texts to unreadable
    }

    private suspend fun job(): SearchIndexJob = settings.preferences.first().searchSettings.indexJob

    private suspend fun updateJob(transform: (SearchIndexJob) -> SearchIndexJob) = settings.update {
        val search = it.searchSettings
        it.copy(searchSettings = search.copy(indexJob = transform(search.indexJob)))
    }

    /** Queues every book that belongs in the index, dropping stale index rows of those that don't. */
    suspend fun startReindex() {
        val prefs = settings.preferences.first().searchSettings
        if (!prefs.indexEnabled) return
        val (eligible, skipped) = library.observeBooks().first()
            .partition { it.hasSearchableText() && prefs.indexes(it.id) }
        skipped.forEach { search.removeBook(it.id) }
        updateJob { SearchIndexJob(pending = eligible.map { it.id.value }, total = eligible.size) }
        scheduleWork()
    }

    /** Chapters of books that belong in the index but have no index entry, by book. */
    suspend fun unindexedChapters(): Map<BookId, List<Chapter>> {
        val prefs = settings.preferences.first().searchSettings
        val indexed = search.indexedChapterIds()
        val eligible = library.observeBooks().first().filter { it.hasSearchableText() && prefs.indexes(it.id) }
            .map { it.id }.toSet()
        return library.observeAllChapters().first()
            .filter { it.bookId in eligible && it.id.value !in indexed }
            .groupBy { it.bookId }
    }

    /** Queues just the chapters missing from the index (e.g. ones that failed to load before) to be tried again. */
    suspend fun retryUnindexed() {
        if (!settings.preferences.first().searchSettings.indexEnabled) return
        val missing = unindexedChapters()
        if (missing.isEmpty()) return
        updateJob { SearchIndexJob(pending = missing.keys.map { it.value }, total = missing.size, onlyMissing = true) }
        scheduleWork()
    }

    suspend fun pause() {
        updateJob { it.copy(paused = true) }
        cancelWork()
    }

    suspend fun resume() {
        updateJob { it.copy(paused = false) }
        scheduleWork()
    }

    /** Makes sure unfinished work is queued with the system, e.g. after the process was killed. */
    suspend fun ensureScheduled() {
        if (job().running) scheduleWork()
    }

    /** Takes a book out of the index and out of any reindex under way, e.g. once it's excluded. */
    suspend fun forget(bookId: BookId) {
        search.removeBook(bookId)
        updateJob { it.copy(pending = it.pending - bookId.value) }
    }

    suspend fun clearIndex() {
        search.clear()
        updateJob { SearchIndexJob() }
        cancelWork()
    }

    /** Indexes queued books one at a time until the queue is empty or paused; safe to cancel at any point. */
    suspend fun runQueue(onProgress: suspend (SearchIndexJob) -> Unit = {}) {
        while (true) {
            val current = job()
            onProgress(current)
            if (!current.running || !indexNext(current)) break
        }
    }

    /** Indexes the first pending book. Returns false when there is nothing sensible left to do. */
    private suspend fun indexNext(current: SearchIndexJob): Boolean {
        val id = BookId(current.pending.first())
        val book = library.findById(id)
        val prefs = settings.preferences.first().searchSettings
        var unreadable = 0
        if (book != null && book.hasSearchableText() && prefs.indexes(id)) {
            val all = library.chapters(id)
            val chapters = if (current.onlyMissing) {
                search.indexedChapterIds(id).let { indexed -> all.filter { it.id.value !in indexed } }
            } else {
                all
            }
            val (texts, failed) = loadTexts(book, chapters)
            if (current.onlyMissing) search.addChapters(id, texts) else search.replaceBook(id, texts)
            unreadable = failed
        } else {
            search.removeBook(id)
        }
        updateJob {
            it.copy(pending = it.pending - id.value, unreadableChapters = it.unreadableChapters + unreadable)
        }
        return true
    }
}
