package dev.reader.data.room.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey val id: String,
    val title: String,
    val author: String,
    val coverPath: String?,
    val format: String,
    val sourcePath: String,
    val isLightNovel: Boolean,
    val pdfRenderMode: String?,
    val addedAt: Long,
)

@Entity(tableName = "chapters")
data class ChapterEntity(
    @PrimaryKey val id: String,
    val bookId: String,
    val title: String,
    val sourcePath: String,
    val orderIndex: Int,
    val wordCount: Int,
)

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val orderIndex: Int,
    val isDefault: Boolean,
)

@Entity(tableName = "book_categories", primaryKeys = ["bookId", "categoryId"])
data class BookCategoryCrossRef(
    val bookId: String,
    val categoryId: String,
)

@Entity(tableName = "bookmarks")
data class BookmarkEntity(
    @PrimaryKey val id: String,
    val bookId: String,
    val chapterId: String,
    val position: Float,
    val note: String,
    val createdAt: Long,
)

/** One row per book: where the reader currently is. */
@Entity(tableName = "progress")
data class ProgressEntity(
    @PrimaryKey val bookId: String,
    val chapterId: String,
    val position: Float,
    val updatedAt: Long,
    val finished: Boolean,
)

/** One row per (book, chapter): preserves position within that chapter even after swiping away. */
@Entity(tableName = "chapter_progress", primaryKeys = ["bookId", "chapterId"])
data class ChapterProgressEntity(
    val bookId: String,
    val chapterId: String,
    val position: Float,
    val updatedAt: Long,
    val finished: Boolean,
)

@Entity(tableName = "reading_sessions")
data class ReadingSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: String,
    val chapterId: String,
    val startedAt: Long,
    val endedAt: Long,
    val wordsRead: Int,
)
