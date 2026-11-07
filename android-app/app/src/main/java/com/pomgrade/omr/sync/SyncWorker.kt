package com.pomgrade.omr.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.pomgrade.omr.OmrApplication
import java.util.concurrent.TimeUnit

/**
 * WorkManager Background Sync Worker.
 * Ensures reliable upload of pending scans with exponential backoff and network constraints.
 *
 * Credit: จัดทำโดย ครูสุวรรณษา เดชรักษา
 */
class SyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? OmrApplication ?: return Result.failure()
        val prefs = applicationContext.getSharedPreferences("omr_settings", Context.MODE_PRIVATE)

        val backendUrl = prefs.getString("backend_url", "") ?: ""
        val deviceId = prefs.getString("device_id", "Android_OMR_Scanner") ?: "Android_OMR_Scanner"

        if (backendUrl.isBlank()) {
            return Result.failure()
        }

        return try {
            val syncResult = app.scanRepository.syncPendingScans(backendUrl, deviceId)
            if (syncResult.isSuccess) {
                Result.success()
            } else {
                Result.retry()
            }
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val WORK_NAME = "OMR_SYNC_WORK"

        fun scheduleSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val syncRequest = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(
                    androidx.work.BackoffPolicy.EXPONENTIAL,
                    15,
                    TimeUnit.SECONDS
                )
                .build()

            WorkManager.getInstance(context).enqueue(syncRequest)
        }
    }
}

