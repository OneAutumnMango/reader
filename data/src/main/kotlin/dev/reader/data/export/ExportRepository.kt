package dev.reader.data.export

import androidx.room.withTransaction
import dev.reader.core.stats.ReadingSessionEvent
import dev.reader.data.repo.SettingsRepository
import dev.reader.data.room.ReaderDatabase
import dev.reader.data.room.entity.BookCategoryCrossRef
import dev.reader.data.room.toBook
import dev.reader.data.room.toBookmark
import dev.reader.data.room.toCategory
import dev.reader.data.room.toChapter
import dev.reader.data.room.toChapterEntity
import dev.reader.data.room.toEntity
import dev.reader.data.room.toEvent
import dev.reader.data.room.toProgress
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest
import java.util.Base64

private const val BYTE_MASK = 0xff

/**
 * Exports/imports app data as one JSON bundle: everything (settings, library metadata, progress,
 * stats) or any subset of [ExportScope]s. Importing merges; [replaceAll] wipes first (used by backups).
 */
class ExportRepository(
    private val database: ReaderDatabase,
    private val settings: SettingsRepository,
    private val json: Json = Json {
        encodeDefaults = true
        prettyPrint = true
        ignoreUnknownKeys = true
    },
    private val coverDirectory: File? = null,
) {

    suspend fun export(scopes: Set<ExportScope> = ExportScope.entries.toSet()): String = encode(bundle(scopes))

    suspend fun import(text: String) = apply(decode(text))

    fun encode(bundle: ExportBundle): String = json.encodeToString(ExportBundle.serializer(), bundle)

    fun decode(text: String): ExportBundle = json.decodeFromString(ExportBundle.serializer(), text)

    suspend fun bundle(scopes: Set<ExportScope> = ExportScope.entries.toSet()): ExportBundle {
        var bundle = ExportBundle(scopes = scopes)
        if (ExportScope.LIBRARY in scopes) bundle = withLibrary(bundle)
        if (ExportScope.STATS in scopes) {
            bundle = bundle.copy(sessions = database.sessions().observeAll().first().map { it.toEvent() })
        }
        if (ExportScope.SETTINGS in scopes) bundle = bundle.copy(preferences = settings.preferences.first())
        return bundle
    }

    /** Clears every table, then applies [bundle] — the result mirrors the bundle exactly. */
    suspend fun replaceAll(bundle: ExportBundle) {
        database.withTransaction {
            with(database.maintenance()) {
                deleteBookCategories()
                deleteBookmarks()
                deleteProgress()
                deleteChapterProgress()
                deleteSearchIndex()
                deleteChapters()
                deleteBooks()
                deleteCategories()
                deleteSessions()
            }
        }
        apply(bundle)
    }

    private suspend fun withLibrary(bundle: ExportBundle): ExportBundle {
        val bookEntities = database.books().observeAll().first()
        val books = bookEntities.map { it.toBook(database.books().categoryIdsFor(it.id)) }
        return bundle.copy(
            books = books,
            coverAssets = books.mapNotNull { book ->
                val cover = book.coverPath?.let(::File)?.takeIf { it.isFile } ?: return@mapNotNull null
                book.id.value to Base64.getEncoder().encodeToString(cover.readBytes())
            }.toMap(),
            chapters = bookEntities.flatMap { database.chapters().getByBook(it.id) }.map { it.toChapter() },
            categories = database.categories().observeAll().first().map { it.toCategory() },
            bookmarks = bookEntities
                .flatMap { database.bookmarks().observeByBook(it.id).first() }
                .map { it.toBookmark() },
            progress = database.progress().observeAll().first().map { it.toProgress() },
            chapterProgress = database.chapterProgress().all().map { it.toProgress() },
        )
    }

    private suspend fun apply(bundle: ExportBundle) {
        if (ExportScope.LIBRARY in bundle.scopes) applyLibrary(bundle.withRestoredCovers())
        if (ExportScope.STATS in bundle.scopes) applySessions(bundle.sessions)
        if (ExportScope.SETTINGS in bundle.scopes) bundle.preferences?.let { prefs -> settings.update { prefs } }
    }

    private fun ExportBundle.withRestoredCovers(): ExportBundle {
        val directory = coverDirectory ?: return this
        return copy(
            books = books.map { book ->
                val encoded = coverAssets[book.id.value] ?: return@map book
                val bytes = runCatching { Base64.getDecoder().decode(encoded) }.getOrNull() ?: return@map book
                val cover = File(directory, book.id.value.sha256()).apply {
                    parentFile?.mkdirs()
                    writeBytes(bytes)
                }
                book.copy(coverPath = cover.path)
            }
        )
    }

    private fun String.sha256(): String = MessageDigest.getInstance("SHA-256")
        .digest(toByteArray())
        .joinToString("") { "%02x".format(it.toInt() and BYTE_MASK) }

    private suspend fun applyLibrary(bundle: ExportBundle) {
        bundle.categories.forEach { database.categories().upsert(it.toEntity()) }
        bundle.books.forEach { book ->
            database.books().upsert(book.toEntity())
            database.books().clearCategories(book.id.value)
            database.books().insertCategoryRefs(book.categoryIds.map { BookCategoryCrossRef(book.id.value, it.value) })
        }
        database.chapters().upsertAll(bundle.chapters.map { it.toEntity() })
        bundle.bookmarks.forEach { database.bookmarks().upsert(it.toEntity()) }
        bundle.progress.forEach { database.progress().upsert(it.toEntity()) }
        bundle.chapterProgress.forEach { database.chapterProgress().upsert(it.toChapterEntity()) }
    }

    /** Sessions have no stable id, so skip any already recorded to keep repeated imports from double-counting. */
    private suspend fun applySessions(sessions: List<ReadingSessionEvent>) {
        val existing = database.sessions().observeAll().first().map { it.toEvent() }.toSet()
        sessions.filterNot { it in existing }.forEach { database.sessions().insert(it.toEntity()) }
    }
}
