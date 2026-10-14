package dev.reader.data.room

import dev.reader.core.model.BookId
import dev.reader.core.model.Bookmark
import dev.reader.core.model.BookmarkId
import dev.reader.core.model.ChapterId
import dev.reader.core.model.Progress
import dev.reader.core.stats.ReadingSessionEvent
import dev.reader.data.room.entity.BookmarkEntity
import dev.reader.data.room.entity.ChapterProgressEntity
import dev.reader.data.room.entity.ProgressEntity
import dev.reader.data.room.entity.ReadingSessionEntity
import kotlin.time.Instant

fun BookmarkEntity.toBookmark() = Bookmark(
    id = BookmarkId(id),
    bookId = BookId(bookId),
    chapterId = ChapterId(chapterId),
    position = position,
    note = note,
    createdAt = Instant.fromEpochMilliseconds(createdAt),
)

fun Bookmark.toEntity() = BookmarkEntity(
    id = id.value,
    bookId = bookId.value,
    chapterId = chapterId.value,
    position = position,
    note = note,
    createdAt = createdAt.toEpochMilliseconds(),
)

fun ProgressEntity.toProgress() = Progress(
    bookId = BookId(bookId),
    chapterId = ChapterId(chapterId),
    position = position,
    updatedAt = Instant.fromEpochMilliseconds(updatedAt),
    finished = finished,
)

fun Progress.toEntity() = ProgressEntity(
    bookId = bookId.value,
    chapterId = chapterId.value,
    position = position,
    updatedAt = updatedAt.toEpochMilliseconds(),
    finished = finished,
)

fun ChapterProgressEntity.toProgress() = Progress(
    bookId = BookId(bookId),
    chapterId = ChapterId(chapterId),
    position = position,
    updatedAt = Instant.fromEpochMilliseconds(updatedAt),
    finished = finished,
)

fun Progress.toChapterEntity() = ChapterProgressEntity(
    bookId = bookId.value,
    chapterId = chapterId.value,
    position = position,
    updatedAt = updatedAt.toEpochMilliseconds(),
    finished = finished,
)

fun ReadingSessionEntity.toEvent() = ReadingSessionEvent(
    bookId = BookId(bookId),
    chapterId = ChapterId(chapterId),
    startedAt = Instant.fromEpochMilliseconds(startedAt),
    endedAt = Instant.fromEpochMilliseconds(endedAt),
    wordsRead = wordsRead,
)

fun ReadingSessionEvent.toEntity() = ReadingSessionEntity(
    bookId = bookId.value,
    chapterId = chapterId.value,
    startedAt = startedAt.toEpochMilliseconds(),
    endedAt = endedAt.toEpochMilliseconds(),
    wordsRead = wordsRead,
)
