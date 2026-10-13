package dev.reader.core.model

import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
data class Bookmark(
    val id: BookmarkId,
    val bookId: BookId,
    val chapterId: ChapterId,
    /** Fraction of the way through the chapter, 0..1. */
    val position: Float,
    val note: String = "",
    val createdAt: Instant,
)

@Serializable
data class Progress(
    val bookId: BookId,
    val chapterId: ChapterId,
    /** Fraction of the way through the chapter, 0..1. */
    val position: Float,
    val updatedAt: Instant,
    val finished: Boolean = false,
)
