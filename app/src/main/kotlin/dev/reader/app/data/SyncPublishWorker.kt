package dev.reader.app.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.reader.app.ReaderApplication
import java.io.IOException
import java.util.concurrent.TimeUnit

private const val SYNC_PUBLISH_WORK = "reader-sync-publish"
private const val MAX_ATTEMPTS = 3

/** Lets pending progress saves finish before publishing. */
private const val DELAY_SECONDS = 5L

/** Publishes this install's sync file after the app is left. It never changes the library. */
class SyncPublishWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result = try {
        if ((applicationContext as ReaderApplication).graph.publishSync()) {
            Result.success()
        } else {
            AppLog.warn("Sync folder is missing or permission was lost", "Sync")
            Result.failure()
        }
    } catch (e: IOException) {
        AppLog.warn("Publishing the sync file failed", "Sync", e)
        if (runAttemptCount < MAX_ATTEMPTS - 1) Result.retry() else Result.failure()
    }
}

object SyncPublishScheduler {

    fun schedule(context: Context) {
        val request = OneTimeWorkRequestBuilder<SyncPublishWorker>()
            .setInitialDelay(DELAY_SECONDS, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniqueWork(SYNC_PUBLISH_WORK, ExistingWorkPolicy.REPLACE, request)
    }
}
