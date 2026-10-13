package dev.reader.data.repo

import dev.reader.core.model.Book
import dev.reader.core.model.BookId
import dev.reader.core.model.Category
import dev.reader.core.model.CategoryId
import dev.reader.core.model.Chapter
import dev.reader.data.room.ReaderDatabase
import dev.reader.data.room.entity.BookCategoryCrossRef
import dev.reader.data.room.toBook
import dev.reader.data.room.toCategory
import dev.reader.data.room.toChapter
import dev.reader.data.room.toEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class LibraryRepository(private val database: ReaderDatabase) {

    fun observeBooks(): Flow<List<Book>> = database.books().observeAll().map { it.toBooks() }

    fun observeBooksInCategory(categoryId: CategoryId): Flow<List<Book>> =
        database.books().observeByCategory(categoryId.value).map { it.toBooks() }

    fun observeChapters(bookId: BookId): Flow<List<Chapter>> =
        database.chapters().observeByBook(bookId.value).map { chapters -> chapters.map { it.toChapter() } }

    fun observeCategories(): Flow<List<Category>> =
        database.categories().observeAll().map { categories -> categories.map { it.toCategory() } }

    suspend fun upsertBook(book: Book, chapters: List<Chapter>) {
        database.books().upsert(book.toEntity())
        database.books().clearCategories(book.id.value)
        database.books().insertCategoryRefs(book.categoryIds.map { BookCategoryCrossRef(book.id.value, it.value) })
        database.chapters().deleteForBook(book.id.value)
        database.chapters().upsertAll(chapters.map { it.toEntity() })
    }

    suspend fun deleteBook(bookId: BookId) {
        database.books().getById(bookId.value)?.let { database.books().delete(it) }
        database.chapters().deleteForBook(bookId.value)
        database.books().clearCategories(bookId.value)
    }

    /** Updates title/author/cover/etc. without touching chapters or category links. */
    suspend fun updateBookDetails(book: Book) = database.books().upsert(book.toEntity())

    suspend fun updateCategories(bookId: BookId, categoryIds: Set<CategoryId>) {
        database.books().clearCategories(bookId.value)
        database.books().insertCategoryRefs(categoryIds.map { BookCategoryCrossRef(bookId.value, it.value) })
    }

    suspend fun upsertCategory(category: Category) = database.categories().upsert(category.toEntity())

    private suspend fun List<dev.reader.data.room.entity.BookEntity>.toBooks(): List<Book> =
        map { it.toBook(database.books().categoryIdsFor(it.id)) }
}
