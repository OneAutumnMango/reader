package dev.reader.data.repo

import androidx.room.withTransaction
import dev.reader.core.model.BookId
import dev.reader.core.model.ChapterId
import dev.reader.data.room.ReaderDatabase

/** One chapter's stored text, one block per line; [rowId] is the position to continue paging from. */
data class IndexedChapter(val rowId: Int, val chapterId: ChapterId, val bookId: BookId, val body: String)

/** The indexed text of [bookId], in bytes, over [chapters] chapters. */
data class BookIndexSize(val bookId: BookId, val chapters: Int, val bytes: Long)

/** The text index behind library search: a copy of each chapter's plain text. */
class SearchRepository(private val database: ReaderDatabase) {

    /** Replaces everything indexed for [bookId] with [chapters] (chapter id to its indexed text). */
    suspend fun replaceBook(bookId: BookId, chapters: List<Pair<ChapterId, String>>) = database.withTransaction {
        database.search().removeBook(bookId.value)
        chapters.forEach { (chapterId, text) -> database.search().insert(chapterId.value, bookId.value, text) }
    }

    /** Adds [chapters] to what's already indexed for [bookId], without touching its other chapters. */
    suspend fun addChapters(bookId: BookId, chapters: List<Pair<ChapterId, String>>) = database.withTransaction {
        chapters.forEach { (chapterId, text) -> database.search().insert(chapterId.value, bookId.value, text) }
    }

    suspend fun indexedChapterIds(): Set<String> = database.search().indexedChapterIds().toSet()

    suspend fun indexedChapterIds(bookId: BookId): Set<String> =
        database.search().indexedChapterIds(bookId.value).toSet()

    suspend fun removeBook(bookId: BookId) = database.search().removeBook(bookId.value)

    suspend fun page(after: Int, limit: Int): List<IndexedChapter> = database.search().page(after, limit).map {
        IndexedChapter(it.rowId, ChapterId(it.chapterId), BookId(it.bookId), it.body)
    }

    suspend fun clear() = database.search().clear()

    /** Per-book index sizes, largest first. */
    suspend fun sizes(): List<BookIndexSize> = database.search().sizes().map {
        BookIndexSize(BookId(it.bookId), it.chapters, it.bytes)
    }
}
