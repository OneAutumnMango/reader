package dev.reader.app.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.reader.app.ReaderApplication
import dev.reader.data.store.PreferencesFile
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

private const val AUTOMATIC_BACKUP_WORK = "reader-automatic-backup"
private const val BACKUP_PREFIX = "reader-auto-"
private const val MAX_AUTOMATIC_BACKUPS = 10

class AutomaticBackupWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result = runCatching {
        val graph = (applicationContext as ReaderApplication).graph
        val preferences = graph.settings.preferences.first()
        if (!preferences.automaticBackupsEnabled) return Result.success()
        val folderUri = preferences.automaticBackupFolderUri ?: return Result.failure()
        val folder = DocumentFile.fromTreeUri(applicationContext, Uri.parse(folderUri))
            ?.takeIf { it.isDirectory && it.canWrite() }
            ?: return Result.retry()
        val name = "$BACKUP_PREFIX${System.currentTimeMillis()}.zip"
        val backup = folder.createFile("application/zip", name) ?: return Result.retry()
        val output = applicationContext.contentResolver.openOutputStream(backup.uri) ?: return Result.retry()
        output.use { graph.backups.write(it, preferences.automaticBackupIncludeBooks) }
        folder.listFiles()
            .filter { it.name.orEmpty().startsWith(BACKUP_PREFIX) }
            .sortedByDescending { it.lastModified() }
            .drop(MAX_AUTOMATIC_BACKUPS)
            .forEach(DocumentFile::delete)
        Result.success()
    }.getOrElse { Result.retry() }
}
object AutomaticBackupScheduler {

    fun update(context: Context, preferences: PreferencesFile) {
        val manager = WorkManager.getInstance(context.applicationContext)
        if (!preferences.automaticBackupsEnabled || preferences.automaticBackupFolderUri.isNullOrBlank()) {
            manager.cancelUniqueWork(AUTOMATIC_BACKUP_WORK)
            return
        }
        val request = PeriodicWorkRequestBuilder<AutomaticBackupWorker>(
            preferences.automaticBackupFrequency.intervalDays,
            TimeUnit.DAYS,
        ).build()
        manager.enqueueUniquePeriodicWork(AUTOMATIC_BACKUP_WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
    }
}
