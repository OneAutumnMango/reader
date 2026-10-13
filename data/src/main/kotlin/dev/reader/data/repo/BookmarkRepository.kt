package dev.reader.data.repo

import dev.reader.core.model.BookId
import dev.reader.core.model.Bookmark
import dev.reader.data.room.ReaderDatabase
import dev.reader.data.room.toBookmark
import dev.reader.data.room.toEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class BookmarkRepository(private val database: ReaderDatabase) {

    fun observe(bookId: BookId): Flow<List<Bookmark>> =
        database.bookmarks().observeByBook(bookId.value).map { bookmarks -> bookmarks.map { it.toBookmark() } }

    suspend fun add(bookmark: Bookmark) = database.bookmarks().upsert(bookmark.toEntity())

    suspend fun remove(bookmark: Bookmark) = database.bookmarks().delete(bookmark.toEntity())
}
