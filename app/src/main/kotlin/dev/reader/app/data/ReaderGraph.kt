package dev.reader.app.data

import android.content.Context
import androidx.room.Room
import dev.reader.core.model.Progress
import dev.reader.core.stats.ReadingSessionEvent
import dev.reader.data.export.BackupRepository
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
import kotlinx.coroutines.launch
import okio.FileSystem
import okio.Path.Companion.toPath
import java.util.concurrent.atomic.AtomicBoolean

/** The app's one dependency graph: a single Room database and DataStore, shared everywhere. */
class ReaderGraph(context: Context) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database: ReaderDatabase = Room.databaseBuilder(
        context.applicationContext,
        ReaderDatabase::class.java,
        "reader.db",
    ).addMigrations(
        ReaderDatabase.MIGRATION_2_3,
        ReaderDatabase.MIGRATION_3_4,
        ReaderDatabase.MIGRATION_4_5,
        ReaderDatabase.MIGRATION_5_6,
        ReaderDatabase.MIGRATION_6_7,
    )
        .fallbackToDestructiveMigration(true)
        .build()

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
    val backups = BackupRepository(exporter, context.applicationContext.filesDir)
    val importer = BookImporter(library)
    val rootFolderScanner = RootFolderScanner(context.applicationContext, settings, library, importer)

    private val launchScanStarted = AtomicBoolean(false)

    /** Runs once per process in the app scope, so rotation or leaving the screen can't cancel it. */
    fun scanRootFoldersOnLaunch() {
        if (!launchScanStarted.compareAndSet(false, true)) return
        scope.launch(Dispatchers.IO) {
            AppLog.debug("Starting launch-time library refresh", "Startup")
            runCatching { importer.refreshLegacyBooks(appContext) }
                .onFailure { AppLog.error("Legacy book refresh failed", "Startup", it) }
            runCatching { rootFolderScanner.scanAll() }
                .onSuccess { AppLog.info("Launch-time library refresh complete", "Startup") }
                .onFailure { AppLog.error("Launch-time library scan failed", "Startup", it) }
        }
    }

    fun saveChapterProgress(progress: Progress) {
        scope.launch(Dispatchers.IO) { this@ReaderGraph.progress.updateChapterProgress(progress) }
    }

    fun recordReadingSession(session: ReadingSessionEvent) {
        scope.launch(Dispatchers.IO) { stats.recordSession(session) }
    }
}
