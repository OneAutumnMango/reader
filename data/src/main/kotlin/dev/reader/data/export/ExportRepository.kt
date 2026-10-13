package dev.reader.data.export

import dev.reader.data.repo.SettingsRepository
import dev.reader.data.room.ReaderDatabase
import dev.reader.data.room.entity.BookCategoryCrossRef
import dev.reader.data.room.toBook
import dev.reader.data.room.toBookmark
import dev.reader.data.room.toCategory
import dev.reader.data.room.toChapter
import dev.reader.data.room.toEntity
import dev.reader.data.room.toProgress
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json

/** Exports/imports the whole library (metadata, bookmarks, progress, settings) as one JSON bundle. */
class ExportRepository(
    private val database: ReaderDatabase,
    private val settings: SettingsRepository,
    private val json: Json = Json {
        encodeDefaults = true
        prettyPrint = true
    },
) {

    suspend fun export(): String {
        val bookEntities = database.books().observeAll().first()
        val books = bookEntities.map { it.toBook(database.books().categoryIdsFor(it.id)) }
        val chapters = bookEntities
            .flatMap { database.chapters().observeByBook(it.id).first() }
            .map { it.toChapter() }
        val categories = database.categories().observeAll().first().map { it.toCategory() }
        val bookmarks = bookEntities
            .flatMap { database.bookmarks().observeByBook(it.id).first() }
            .map { it.toBookmark() }
        val progress = bookEntities.mapNotNull { database.progress().observe(it.id).first() }.map { it.toProgress() }

        val bundle = ExportBundle(
            books = books,
            chapters = chapters,
            categories = categories,
            bookmarks = bookmarks,
            progress = progress,
            preferences = settings.preferences.first(),
        )
        return json.encodeToString(ExportBundle.serializer(), bundle)
    }

    suspend fun import(text: String) {
        val bundle = json.decodeFromString(ExportBundle.serializer(), text)

        bundle.categories.forEach { database.categories().upsert(it.toEntity()) }
        bundle.books.forEach { book ->
            database.books().upsert(book.toEntity())
            database.books().clearCategories(book.id.value)
            val refs = book.categoryIds.map { BookCategoryCrossRef(book.id.value, it.value) }
            database.books().insertCategoryRefs(refs)
        }
        database.chapters().upsertAll(bundle.chapters.map { it.toEntity() })
        bundle.bookmarks.forEach { database.bookmarks().upsert(it.toEntity()) }
        bundle.progress.forEach { database.progress().upsert(it.toEntity()) }
        settings.update { bundle.preferences }
    }
}
