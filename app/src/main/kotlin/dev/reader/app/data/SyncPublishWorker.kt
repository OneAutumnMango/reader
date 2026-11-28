package dev.reader.app.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker
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

    override suspend fun doWork(): Result = publishResult(runAttemptCount) {
        (applicationContext as ReaderApplication).graph.publishSync()
    }
}

/** [publish] is [ReaderGraph.publishSync]; [attempt] counts from zero. */
internal suspend fun publishResult(attempt: Int, publish: suspend () -> Boolean): ListenableWorker.Result = try {
    if (publish()) {
        ListenableWorker.Result.success()
    } else {
        AppLog.warn("Sync folder is missing or permission was lost", "Sync")
        ListenableWorker.Result.failure()
    }
} catch (e: IOException) {
    AppLog.warn("Publishing the sync file failed", "Sync", e)
    if (attempt < MAX_ATTEMPTS - 1) ListenableWorker.Result.retry() else ListenableWorker.Result.failure()
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
