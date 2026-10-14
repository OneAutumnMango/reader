package dev.reader.app.data

import android.content.Context
import androidx.room.Room
import dev.reader.data.export.ExportRepository
import dev.reader.data.importer.BookImporter
import dev.reader.data.repo.BookmarkRepository
import dev.reader.data.repo.LibraryRepository
import dev.reader.data.repo.ProgressRepository
import dev.reader.data.repo.SearchRepository
import dev.reader.data.repo.SettingsRepository
import dev.reader.data.repo.StatsRepository
import dev.reader.data.room.ReaderDatabase
import dev.reader.data.store.ReaderStores
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okio.FileSystem
import okio.Path.Companion.toPath

/** The app's one dependency graph: a single Room database and DataStore, shared everywhere. */
class ReaderGraph(context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database: ReaderDatabase = Room.databaseBuilder(
        context.applicationContext,
        ReaderDatabase::class.java,
        "reader.db",
    ).addMigrations(ReaderDatabase.MIGRATION_2_3).fallbackToDestructiveMigration(true).build()

    private val stores = ReaderStores(
        fileSystem = FileSystem.SYSTEM,
        directory = context.applicationContext.filesDir.absolutePath.toPath(),
        scope = scope,
    )

    val library = LibraryRepository(database)
    val progress = ProgressRepository(database)
    val bookmarks = BookmarkRepository(database)
    val stats = StatsRepository(database)
    val search = SearchRepository(database)
    val settings = SettingsRepository(stores)
    val exporter = ExportRepository(database, settings)
    val importer = BookImporter(library)
    val rootFolderScanner = RootFolderScanner(context.applicationContext, settings, importer)
}
