package dev.reader.app.data

import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.documentfile.provider.DocumentFile
import androidx.room.Room
import dev.reader.app.ui.sync.SyncController
import dev.reader.core.model.Book
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
import dev.reader.data.sync.SyncEngine
import dev.reader.data.sync.SyncLibrary
import dev.reader.data.sync.SyncStateStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path.Companion.toPath
import java.io.File
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
        ReaderDatabase.MIGRATION_1_2,
        ReaderDatabase.MIGRATION_2_3,
        ReaderDatabase.MIGRATION_3_4,
        ReaderDatabase.MIGRATION_4_5,
        ReaderDatabase.MIGRATION_5_6,
        ReaderDatabase.MIGRATION_6_7,
        ReaderDatabase.MIGRATION_7_8,
    )
        // No destructive fallback: a missing or broken migration should crash loudly, not
        // silently drop the user's whole library, progress and stats.
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
    val exporter = ExportRepository(database, settings, coverDirectory = File(appContext.filesDir, "covers"))
    val backups = BackupRepository(exporter, context.applicationContext.filesDir)
    val indexer = LibraryIndexer(
        appContext,
        library,
        search,
        settings,
        scheduleWork = { SearchIndexScheduler.schedule(appContext) },
        cancelWork = { SearchIndexScheduler.cancel(appContext) },
    )
    val importer = BookImporter(
        library,
        search = search,
        shouldIndex = { book ->
            settings.preferences.first().searchSettings.let { it.indexOnImport && it.indexes(book.id) }
        },
    )
    private val syncStore = SyncStateStore(File(appContext.noBackupFilesDir, "sync-state.json"), Build.MODEL)

    /** The only instance, since its lock is what keeps the controller and the publish worker apart. */
    private val syncEngine = SyncEngine(
        SyncLibrary(database),
        syncStore,
        oneCategory = { settings.preferences.first().singleCategoryPerBook },
    )
    val sync by lazy {
        SyncController(
            syncEngine,
            syncStore,
            openFolder = { SafSyncFolder.open(appContext, it) },
            folderName = { uri ->
                runCatching { DocumentFile.fromTreeUri(appContext, Uri.parse(uri))?.name }.getOrNull()
                    ?.takeIf(String::isNotBlank)
            },
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
        )
    }
    val coverSearch = CoverSearchService()
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
                .onSuccess { result ->
                    AppLog.info("Launch-time library refresh complete", "Startup")
                    if (result.missingBooks.isNotEmpty()) {
                        AppLog.warn("${result.missingBooks.size} book(s) have a missing source file", "Startup")
                    }
                }
                .onFailure { AppLog.error("Launch-time library scan failed", "Startup", it) }
            runCatching { refreshMissingCovers() }
                .onFailure { AppLog.error("Missing cover refresh failed", "Startup", it) }
        }
    }

    /** Re-parses [book]'s source file, refreshing its chapter list in place (e.g. after a parser fix). */
    suspend fun reimportBook(book: Book): Book = importer.reimport(book, appContext)

    suspend fun refreshMissingCovers(): Int = importer.refreshMissingCovers(appContext) { book ->
        File(book.sourcePath).takeIf { it.isFile && it.canRead() }
            ?: book.originUri?.let { uri ->
                runCatching { ContentFileCopier.copyToCache(appContext, Uri.parse(uri)) }.getOrNull()
            }
    }

    /**
     * Publishes this install's sync file, if a folder is chosen. False if that folder is missing or permission
     * to it was lost; throws IOException if publishing fails otherwise.
     */
    suspend fun publishSync(): Boolean = withContext(Dispatchers.IO) {
        val uri = syncEngine.state().folderUri ?: return@withContext true
        val folder = SafSyncFolder.open(appContext, uri) ?: return@withContext false
        syncEngine.publish(folder)
        true
    }

    fun saveChapterProgress(progress: Progress) {
        scope.launch(Dispatchers.IO) { this@ReaderGraph.progress.updateChapterProgress(progress) }
    }

    fun recordReadingSession(session: ReadingSessionEvent) {
        scope.launch(Dispatchers.IO) { stats.recordSession(session) }
    }
}
