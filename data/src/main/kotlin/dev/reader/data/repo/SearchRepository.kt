package dev.reader.data.repo

import dev.reader.core.model.BookId
import dev.reader.core.model.ChapterId
import dev.reader.data.room.ReaderDatabase
import dev.reader.data.room.entity.ChapterFtsEntity

class SearchRepository(private val database: ReaderDatabase) {

    suspend fun indexChapter(chapterId: ChapterId, bookId: BookId, plainText: String) {
        database.search().removeFromIndex(chapterId.value)
        database.search().index(
            ChapterFtsEntity(rowId = 0, chapterId = chapterId.value, bookId = bookId.value, body = plainText),
        )
    }

    suspend fun search(query: String): List<ChapterId> =
        database.search().search(query).map { ChapterId(it.chapterId) }
}
