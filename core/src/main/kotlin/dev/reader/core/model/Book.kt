package dev.reader.core.model

import dev.reader.core.reader.PdfRenderMode
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/** A single book, a light novel with many chapter files, or a comic archive. */
@Serializable
data class Book(
    val id: BookId,
    val title: String,
    val author: String = "",
    /** User-supplied override; falls back to the parsed cover when null. */
    val coverPath: String? = null,
    val format: BookFormat,
    /** Folder or file this book was imported from. */
    val sourcePath: String,
    val isLightNovel: Boolean = false,
    val categoryIds: Set<CategoryId> = emptySet(),
    /** Only meaningful for [BookFormat.PDF]; null means the app default applies. */
    val pdfRenderMode: PdfRenderMode? = null,
    val addedAt: Instant,
    /** SHA-256 of the imported content, so the same book is recognised even after a rename. */
    val contentHash: String? = null,
    /** Heading regex that splits a txt/markdown book into chapters; null keeps it as one chapter. */
    val chapterPattern: String? = null,
)

@Serializable
data class Chapter(
    val id: ChapterId,
    val bookId: BookId,
    val title: String,
    /** File path for a light novel chapter, or an in-archive entry name otherwise. */
    val sourcePath: String,
    val orderIndex: Int,
    val wordCount: Int = 0,
    /** Front/back matter (title page, copyright, contents…) shown as book info rather than a chapter. */
    val isExtra: Boolean = false,
)
