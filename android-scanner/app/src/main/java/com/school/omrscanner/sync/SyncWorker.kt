package com.school.omrscanner.sync

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.school.omrscanner.MainActivity
import com.school.omrscanner.auth.GoogleAuthProvider
import com.school.omrscanner.data.local.AppDatabase
import com.school.omrscanner.data.local.ScanDao
import com.school.omrscanner.data.local.ScanEntity
import kotlinx.coroutines.delay
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * Background WorkManager Sync Worker.
 *
 * Responsibilities:
 *  - Network constraint: NetworkType.CONNECTED
 *  - Batch transmission: <= 50 items per request
 *  - Jittered exponential backoff: 100-1000 ms random delay to avoid server lock congestion
 *  - Transitions Room entity from sync_status = 0 to 1 upon acceptance or duplicate.
 *
 * Credit: จัดทำโดย ครูสุวรรณษา เดชรักษา
 */
class SyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val DEFAULT_GAS_URL = "https://script.google.com/macros/s/AKfycbwOTILtFa1B5FHuaK7GtsQS6nQ0SaA1WBF627L0re3OZsWfOCdlcesDXlGRT7NjM21boA/exec"
        private const val WORK_NAME = "PomGradeScanSyncWork"
        private const val NOTIFICATION_CHANNEL_ID = "pom_grade_sync_channel"
        private const val NOTIFICATION_ID = 2001

        fun enqueueImmediateSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    15,
                    TimeUnit.SECONDS
                )
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }

    override suspend fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences("pom_grade_prefs", Context.MODE_PRIVATE)
        val backendUrl = prefs.getString("gas_backend_url", DEFAULT_GAS_URL)
            ?.takeIf { it.isNotBlank() } ?: DEFAULT_GAS_URL

        val db = AppDatabase.getInstance(applicationContext)
        val dao = db.scanDao()

        // 1. Fetch pending scans (batches <= 50)
        val pendingScans = dao.getPendingScans(limit = 50)
        if (pendingScans.isEmpty()) {
            return Result.success()
        }

        // 2. Random Jitter (100–1000 ms) before request to prevent concurrent lock storms
        val jitterMs = Random.nextLong(100, 1000)
        delay(jitterMs)

        // 3. Obtain Token & Create Authenticated Retrofit Client
        val authProvider = GoogleAuthProvider(applicationContext)
        if (!authProvider.isUserSignedIn()) {
            showAuthNotification()
            return Result.retry()
        }

        // Silent refresh if needed
        val token = authProvider.getCachedToken()
            ?: authProvider.fetchFreshAccessToken().getOrNull()

        if (token == null) {
            showAuthNotification()
            return Result.retry()
        }

        val okHttpClient = OkHttpProvider.createClient(authProvider)
        val retrofit = Retrofit.Builder()
            .baseUrl("https://script.google.com/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        val apiService = retrofit.create(GasApiService::class.java)

        // 4. Map entities to payload
        val batchItems = pendingScans.map { s ->
            GasScanRecordItem(
                scanId = s.scanId,
                examId = s.examId,
                studentCode = s.studentCode,
                deviceId = s.deviceId,
                ans = s.answers,
                scannedAt = s.scannedAt
            )
        }

        return try {
            val response = apiService.postScanRecords(backendUrl, GasRecordsBatchPayload(batchItems))

            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!

                if (body.status == "BUSY") {
                    return Result.retry() // Server lock was busy, retry with exponential backoff
                }

                if (body.status == "SUCCESS" && body.results != null) {
                    val receivedAt = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).apply {
                        timeZone = java.util.TimeZone.getTimeZone("UTC")
                    }.format(java.util.Date())

                    val acceptedIds = mutableListOf<String>()
                    val rejectedIds = mutableListOf<String>()

                    body.results.forEach { r ->
                        if (r.result == "ACCEPTED" || r.result == "DUPLICATE") {
                            acceptedIds.add(r.scanId)
                        } else {
                            rejectedIds.add(r.scanId)
                        }
                    }

                    // Transition Room entities from sync_status = 0 -> 1
                    if (acceptedIds.isNotEmpty()) {
                        dao.markScansAsSynced(acceptedIds, receivedAt)
                    }

                    if (rejectedIds.isNotEmpty()) {
                        dao.markScansAsRejected(rejectedIds)
                    }

                    Result.success()
                } else {
                    Result.retry()
                }
            } else {
                Result.retry()
            }
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private fun showAuthNotification() {
        try {
            val channelId = "pom_grade_auth_channel"
            val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    channelId,
                    "การยืนยันตัวตน Pom Grade",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "แจ้งเตือนเมื่อ Token หมดอายุและต้องการการลงชื่อเข้าใช้ใหม่"
                }
                notificationManager.createNotificationChannel(channel)
            }

            val intent = Intent(applicationContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                applicationContext,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
            )

            val notification = NotificationCompat.Builder(applicationContext, channelId)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("Pom Grade — ต้องยืนยันตัวตนใหม่")
                .setContentText("กรุณาเปิดแอปเพื่อยืนยันตัวตน Google ใหม่")
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()

            notificationManager.notify(1001, notification)
        } catch (_: Exception) {}
    }
}

