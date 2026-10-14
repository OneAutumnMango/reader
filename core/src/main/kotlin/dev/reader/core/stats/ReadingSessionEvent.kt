package dev.reader.core.stats

import dev.reader.core.model.BookId
import dev.reader.core.model.ChapterId
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/** One continuous span of reading, appended when a reader session ends. */
@Serializable
data class ReadingSessionEvent(
    val bookId: BookId,
    val chapterId: ChapterId,
    val startedAt: Instant,
    val endedAt: Instant,
    val wordsRead: Int,
    val pagesTurned: Int = 0,
)
