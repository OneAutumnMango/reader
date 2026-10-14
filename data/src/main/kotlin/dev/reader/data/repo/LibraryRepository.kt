package dev.reader.data.repo

import androidx.room.withTransaction
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class LibraryRepository(private val database: ReaderDatabase) {

    fun observeBooks(): Flow<List<Book>> = database.books().observeAll().map { it.toBooks() }

    fun observeBooksInCategory(categoryId: CategoryId): Flow<List<Book>> =
        database.books().observeByCategory(categoryId.value).map { it.toBooks() }

    fun observeChapters(bookId: BookId): Flow<List<Chapter>> =
        database.chapters().observeByBook(bookId.value).map { chapters -> chapters.map { it.toChapter() } }

    fun observeCategories(): Flow<List<Category>> =
        database.categories().observeAll().map { categories -> categories.map { it.toCategory() } }

    suspend fun findBySourcePath(sourcePath: String): Book? =
        database.books().findBySourcePath(sourcePath)?.let { it.toBook(database.books().categoryIdsFor(it.id)) }

    suspend fun findByContentHash(hash: String): Book? =
        database.books().findByContentHash(hash)?.let { it.toBook(database.books().categoryIdsFor(it.id)) }

    /** Books imported before content hashing existed. */
    suspend fun booksMissingContentHash(): List<Book> =
        database.books().observeAll().first().filter { it.contentHash == null }.toBooks()

    suspend fun chapters(bookId: BookId): List<Chapter> =
        database.chapters().getByBook(bookId.value).map { it.toChapter() }

    suspend fun upsertBook(book: Book, chapters: List<Chapter>) {
        database.books().upsert(book.toEntity())
        database.books().clearCategories(book.id.value)
        database.books().insertCategoryRefs(book.categoryIds.map { BookCategoryCrossRef(book.id.value, it.value) })
        database.chapters().deleteForBook(book.id.value)
        database.chapters().upsertAll(chapters.map { it.toEntity() })
    }

    /** Removes every book along with its chapters, bookmarks and progress; categories, stats and settings stay. */
    suspend fun removeAllBooks() = database.withTransaction {
        with(database.maintenance()) {
            deleteBookCategories()
            deleteBookmarks()
            deleteProgress()
            deleteChapterProgress()
            deleteSearchIndex()
            deleteChapters()
            deleteBooks()
        }
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

    /** Removes the category and its book links; the books themselves stay in the library. */
    suspend fun deleteCategory(category: Category) {
        database.categories().deleteBookRefs(category.id.value)
        database.categories().delete(category.toEntity())
    }

    /** Persists [ordered] as the new tab order. */
    suspend fun reorderCategories(ordered: List<Category>) =
        ordered.forEachIndexed { index, category -> upsertCategory(category.copy(orderIndex = index)) }

    /** Makes [categoryId] the category new books land in, or clears it when null. */
    suspend fun setDefaultCategory(categoryId: CategoryId?) =
        database.categories().observeAll().first().forEach { entity ->
            upsertCategory(entity.toCategory().copy(isDefault = entity.id == categoryId?.value))
        }

    suspend fun defaultCategoryIds(): Set<CategoryId> =
        database.categories().observeAll().first().filter { it.isDefault }.map { CategoryId(it.id) }.toSet()

    private suspend fun List<dev.reader.data.room.entity.BookEntity>.toBooks(): List<Book> =
        map { it.toBook(database.books().categoryIdsFor(it.id)) }
}
