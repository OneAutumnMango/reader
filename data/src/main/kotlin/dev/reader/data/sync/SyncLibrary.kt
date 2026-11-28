package dev.reader.data.sync

import androidx.room.withTransaction
import dev.reader.core.model.BookId
import dev.reader.core.model.Chapter
import dev.reader.core.model.ChapterId
import dev.reader.core.sync.CategoriesValue
import dev.reader.core.sync.ItemKey
import dev.reader.core.sync.ItemKind
import dev.reader.core.sync.ItemValue
import dev.reader.core.sync.PositionValue
import dev.reader.data.room.ReaderDatabase
import dev.reader.data.room.entity.BookCategoryCrossRef
import dev.reader.data.room.entity.CategoryEntity
import dev.reader.data.room.entity.ChapterProgressEntity
import dev.reader.data.room.entity.ProgressEntity
import dev.reader.data.room.toChapter
import java.security.MessageDigest
import java.util.UUID

data class LocalBook(
    val bookId: BookId,
    val hash: String,
    val title: String,
    val signature: String,
    val chapterIds: Map<Int, ChapterId>,
    val position: ItemValue?,
    val chapters: Map<Int, ItemValue>,
    val categories: CategoriesValue?,
)

data class SkippedBook(val title: String, val reason: String)

data class LibrarySnapshot(val books: Map<String, LocalBook>, val skipped: List<SkippedBook>)

fun chapterSignature(chapters: List<Chapter>): String {
    val text = chapters.sortedBy { it.orderIndex }
        .joinToString(",") { "${it.orderIndex}:${it.wordCount}:${it.isExtra}" }
    return MessageDigest.getInstance("SHA-256").digest(text.encodeToByteArray()).joinToString("") { "%02x".format(it) }
}

private fun matchKey(name: String) = cleanCategoryName(name).lowercase()

/** The categories sync can see, grouped by the name they match on; a group of two or more is ambiguous. */
private fun List<CategoryEntity>.byName() =
    filter { isVisibleName(cleanCategoryName(it.name)) }.groupBy { matchKey(it.name) }

// A bad stored number would make other devices reject this install's whole file.
private fun position(chapter: Int, position: Float, finished: Boolean, updatedAt: Long) = PositionValue(
    chapter,
    if (position.isNaN()) 0f else position.coerceIn(0f, 1f),
    finished,
    updatedAt.coerceAtLeast(0),
)

/** Maps the library to sync items and back. */
class SyncLibrary(
    private val database: ReaderDatabase,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    suspend fun snapshot(): LibrarySnapshot = database.withTransaction {
        val entities = database.books().all()
        val chapters = database.chapters().all().map { it.toChapter() }.groupBy { it.bookId.value }
        val progress = database.progress().all().associateBy { it.bookId }
        val chapterProgress = database.chapterProgress().all().associateBy { it.bookId to it.chapterId }
        val categories = database.categories().all()
        val categoriesById = categories.associateBy { it.id }
        val byName = categories.byName()
        val visible = byName.values.flatten().map { it.id }.toSet()
        val copies = entities.groupingBy { it.contentHash }.eachCount()

        val books = mutableMapOf<String, LocalBook>()
        val skipped = mutableListOf<SkippedBook>()
        for (book in entities) {
            val hash = book.contentHash.orEmpty()
            val reason = when {
                hash.isBlank() -> "No file hash"
                copies.getValue(book.contentHash) > 1 -> "Same file appears twice"
                else -> null
            }
            if (reason != null) {
                skipped += SkippedBook(book.title, reason)
                continue
            }
            val own = chapters[book.id].orEmpty()
            val current = progress[book.id]?.let { row ->
                own.find { it.id.value == row.chapterId }
                    ?.let { position(it.orderIndex, row.position, row.finished, row.updatedAt) }
            }
            val used = database.books().categoryIdsFor(book.id)
                .mapNotNull { categoriesById[it] }
                .filter { it.id in visible }
                .sortedBy { it.orderIndex }
            val ambiguous = used.any { byName.getValue(matchKey(it.name)).size > 1 }
            books[hash] = LocalBook(
                bookId = BookId(book.id),
                hash = hash,
                title = book.title,
                signature = chapterSignature(own),
                chapterIds = own.associate { it.orderIndex to it.id },
                position = current,
                chapters = own.associate { chapter ->
                    val row = chapterProgress[book.id to chapter.id.value]
                    chapter.orderIndex to if (row == null) {
                        position(chapter.orderIndex, 0f, false, 0)
                    } else {
                        position(chapter.orderIndex, row.position, row.finished, row.updatedAt)
                    }
                },
                categories = if (ambiguous) null else CategoriesValue(used.map { cleanCategoryName(it.name) }),
            )
        }
        LibrarySnapshot(books, skipped)
    }

    /** Applies [changes] in one transaction. Anything naming an unknown book or chapter is ignored. */
    suspend fun write(snapshot: LibrarySnapshot, changes: Map<ItemKey, ItemValue?>) = database.withTransaction {
        for ((key, value) in changes) {
            val book = snapshot.books[key.bookHash] ?: continue
            when (key.kind) {
                ItemKind.BOOK_POSITION -> writePosition(book, value)
                ItemKind.CHAPTER -> writeChapter(book, key.chapter, value)
                ItemKind.CATEGORIES -> writeCategories(book.bookId.value, value)
            }
        }
    }

    private suspend fun writePosition(book: LocalBook, value: ItemValue?) {
        if (value == null) return database.progress().delete(book.bookId.value)
        if (value !is PositionValue) return
        val chapterId = book.chapterIds[value.chapter] ?: return
        database.progress().upsert(
            ProgressEntity(book.bookId.value, chapterId.value, value.position, value.updatedAt, value.finished),
        )
    }

    private suspend fun writeChapter(book: LocalBook, chapter: Int, value: ItemValue?) {
        val chapterId = book.chapterIds[chapter] ?: return
        // No value is stored as an untouched row, which a snapshot reads the same as no row.
        val state = if (value == null) PositionValue(chapter, 0f, false, 0) else value as? PositionValue ?: return
        database.chapterProgress().upsert(
            ChapterProgressEntity(book.bookId.value, chapterId.value, state.position, state.updatedAt, state.finished),
        )
    }

    private suspend fun writeCategories(bookId: String, value: ItemValue?) {
        if (value != null && value !is CategoriesValue) return
        val all = database.categories().all()
        val byName = all.byName()
        val visible = byName.values.flatten().associateBy { it.id }
        val (used, hidden) = database.books().categoryIdsFor(bookId).partition { it in visible }
        // Membership cannot be told apart by name, so leave it alone.
        if (used.any { byName.getValue(matchKey(visible.getValue(it).name)).size > 1 }) return

        var nextOrder = (all.maxOfOrNull { it.orderIndex } ?: -1) + 1
        val names = normalizeCategoryNames(value?.names.orEmpty())
        val ids = names.map { name ->
            byName[name.lowercase()]?.first()?.id
                ?: CategoryEntity(newId(), name, nextOrder++, isDefault = false)
                    .also { database.categories().upsert(it) }
                    .id
        }
        database.books().clearCategories(bookId)
        database.books().insertCategoryRefs((hidden + ids).map { BookCategoryCrossRef(bookId, it) })
    }
}
