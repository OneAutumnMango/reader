package dev.reader.data.room

import dev.reader.core.model.Book
import dev.reader.core.model.BookFormat
import dev.reader.core.model.BookId
import dev.reader.core.model.Category
import dev.reader.core.model.CategoryId
import dev.reader.core.model.Chapter
import dev.reader.core.model.ChapterId
import dev.reader.core.reader.PdfRenderMode
import dev.reader.data.room.entity.BookEntity
import dev.reader.data.room.entity.CategoryEntity
import dev.reader.data.room.entity.ChapterEntity
import kotlin.time.Instant

fun BookEntity.toBook(categoryIds: List<String>) = Book(
    id = BookId(id),
    title = title,
    author = author,
    coverPath = coverPath,
    format = BookFormat.valueOf(format),
    sourcePath = sourcePath,
    isLightNovel = isLightNovel,
    categoryIds = categoryIds.map { CategoryId(it) }.toSet(),
    pdfRenderMode = pdfRenderMode?.let { PdfRenderMode.valueOf(it) },
    addedAt = Instant.fromEpochMilliseconds(addedAt),
)

fun Book.toEntity() = BookEntity(
    id = id.value,
    title = title,
    author = author,
    coverPath = coverPath,
    format = format.name,
    sourcePath = sourcePath,
    isLightNovel = isLightNovel,
    pdfRenderMode = pdfRenderMode?.name,
    addedAt = addedAt.toEpochMilliseconds(),
)

fun ChapterEntity.toChapter() = Chapter(
    id = ChapterId(id),
    bookId = BookId(bookId),
    title = title,
    sourcePath = sourcePath,
    orderIndex = orderIndex,
    wordCount = wordCount,
)

fun Chapter.toEntity() = ChapterEntity(
    id = id.value,
    bookId = bookId.value,
    title = title,
    sourcePath = sourcePath,
    orderIndex = orderIndex,
    wordCount = wordCount,
)

fun CategoryEntity.toCategory() = Category(
    id = CategoryId(id),
    name = name,
    orderIndex = orderIndex,
    isDefault = isDefault,
)

fun Category.toEntity() = CategoryEntity(
    id = id.value,
    name = name,
    orderIndex = orderIndex,
    isDefault = isDefault,
)
