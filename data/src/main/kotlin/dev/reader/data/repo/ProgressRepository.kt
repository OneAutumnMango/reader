package dev.reader.data.repo

import dev.reader.core.model.BookId
import dev.reader.core.model.ChapterId
import dev.reader.core.model.Progress
import dev.reader.data.room.ReaderDatabase
import dev.reader.data.room.toChapterEntity
import dev.reader.data.room.toEntity
import dev.reader.data.room.toProgress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ProgressRepository(private val database: ReaderDatabase) {

    fun observe(bookId: BookId): Flow<Progress?> =
        database.progress().observe(bookId.value).map { it?.toProgress() }

    fun observeAll(): Flow<Map<BookId, Progress>> = database.progress().observeAll().map { entities ->
        entities.associate { BookId(it.bookId) to it.toProgress() }
    }

    fun observeFinishedCount(): Flow<Int> = database.progress().observeFinishedCount()

    suspend fun update(progress: Progress) = database.progress().upsert(progress.toEntity())

    /** Per-chapter position, preserved independently so swiping between chapters doesn't reset it. */
    fun observeChapterProgress(bookId: BookId, chapterId: ChapterId): Flow<Progress?> =
        database.chapterProgress().observe(bookId.value, chapterId.value).map { it?.toProgress() }

    suspend fun updateChapterProgress(progress: Progress) =
        database.chapterProgress().upsert(progress.toChapterEntity())
}
