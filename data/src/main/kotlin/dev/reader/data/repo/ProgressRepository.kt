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
import kotlin.time.Instant

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

    fun observeChapterProgress(bookId: BookId): Flow<Map<ChapterId, Progress>> =
        database.chapterProgress().observeForBook(bookId.value).map { rows ->
            rows.associate { ChapterId(it.chapterId) to it.toProgress() }
        }

    /** 0..1 through each book, from its chapters' progress. */
    fun observeBookFractions(): Flow<Map<BookId, Float>> =
        database.chapterProgress().observeBookFractions().map { rows ->
            rows.associate { BookId(it.bookId) to it.fraction }
        }

    /** Marks [chapterIds] read (finished, at the end) or unread (back to the start). */
    suspend fun setChaptersRead(bookId: BookId, chapterIds: List<ChapterId>, read: Boolean, now: Instant) =
        database.chapterProgress().upsertAll(
            chapterIds.map { id ->
                Progress(bookId, id, position = if (read) 1f else 0f, updatedAt = now, finished = read)
                    .toChapterEntity()
            },
        )
}
