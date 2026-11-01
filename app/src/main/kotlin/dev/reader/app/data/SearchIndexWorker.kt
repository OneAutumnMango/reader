package dev.reader.app.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.reader.app.ReaderApplication
import dev.reader.data.store.SearchIndexJob

private const val SEARCH_INDEX_WORK = "reader-search-index"
private const val CHANNEL_ID = "search-index"
private const val NOTIFICATION_ID = 7

/** Runs the library reindex in the background, so it carries on when the app is closed. */
class SearchIndexWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result {
        val indexer = (applicationContext as ReaderApplication).graph.indexer
        runCatching { setForeground(foregroundInfo(null)) }
        indexer.runQueue { job -> runCatching { setForeground(foregroundInfo(job)) } }
        return Result.success()
    }

    override suspend fun getForegroundInfo(): ForegroundInfo = foregroundInfo(null)

    private fun foregroundInfo(job: SearchIndexJob?): ForegroundInfo {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Search indexing", NotificationManager.IMPORTANCE_LOW),
        )
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("Indexing your library")
            .setContentText(job?.let { "${it.done} of ${it.total} books" } ?: "Getting started")
            .setProgress(job?.total ?: 0, job?.done ?: 0, job == null)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }
}

object SearchIndexScheduler {

    fun schedule(context: Context) {
        val request = OneTimeWorkRequestBuilder<SearchIndexWorker>().build()
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniqueWork(SEARCH_INDEX_WORK, ExistingWorkPolicy.KEEP, request)
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork(SEARCH_INDEX_WORK)
    }
}
