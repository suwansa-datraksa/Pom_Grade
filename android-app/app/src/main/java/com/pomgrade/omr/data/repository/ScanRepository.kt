package com.pomgrade.omr.data.repository

import com.google.gson.Gson
import com.pomgrade.omr.data.local.ScanDao
import com.pomgrade.omr.data.local.ScanEntity
import com.pomgrade.omr.data.local.SyncStatus
import com.pomgrade.omr.data.remote.GasApiService
import com.pomgrade.omr.data.remote.GasScanBatchRequest
import com.pomgrade.omr.data.remote.GasScanItemRequest
import com.pomgrade.omr.data.remote.GasScanMetrics
import com.pomgrade.omr.omr.OmrProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Single source of truth for Scan data and Cloud Sync orchestration.
 *
 * Credit: จัดทำโดย ครูสุวรรณษา เดชรักษา
 */
class ScanRepository(
    private val scanDao: ScanDao,
    private val apiService: GasApiService,
    private val gson: Gson = Gson()
) {

    val allScansFlow: Flow<List<ScanEntity>> = scanDao.getAllScansFlow()
    val totalScansCountFlow: Flow<Int> = scanDao.getTotalScansCountFlow()
    val pendingScansCountFlow: Flow<Int> = scanDao.getPendingScansCountFlow()

    private val isoDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    /**
     * Persists a newly completed scan into local Room database.
     */
    suspend fun saveScan(result: OmrProcessor.ProcessResult): Boolean = withContext(Dispatchers.IO) {
        val nowIso = isoDateFormat.format(Date())

        val metricsMap = mapOf(
            "confidence" to result.confidence,
            "skew_angle" to result.skewAngle,
            "processing_time_ms" to result.processingTimeMs
        )

        val entity = ScanEntity(
            scanId = result.scanId,
            examId = result.examId,
            studentId = result.studentId,
            score = result.score,
            totalQuestions = result.totalQuestions,
            percentage = result.percentage,
            answersJson = gson.toJson(result.answers),
            metricsJson = gson.toJson(metricsMap),
            scannedAt = nowIso,
            syncStatus = SyncStatus.PENDING
        )

        val rowId = scanDao.insertScan(entity)
        rowId > 0
    }

    /**
     * Syncs all pending scans to Google Apps Script cloud backend.
     */
    suspend fun syncPendingScans(
        backendUrl: String,
        deviceId: String
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val pending = scanDao.getPendingScans()
            if (pending.isEmpty()) {
                return@withContext Result.success(0)
            }

            // Group by examId to send batches per exam
            val groupedByExam = pending.groupBy { it.examId }
            var totalSyncedCount = 0

            for ((examId, examScans) in groupedByExam) {
                val scanItems = examScans.map { s ->
                    val answersList: List<String> = try {
                        gson.fromJson(s.answersJson, Array<String>::class.java).toList()
                    } catch (e: Exception) {
                        emptyList()
                    }

                    GasScanItemRequest(
                        scanId = s.scanId,
                        studentId = s.studentId,
                        score = s.score,
                        totalQuestions = s.totalQuestions,
                        answers = answersList,
                        metrics = GasScanMetrics(
                            confidence = 0.95,
                            skewAngle = 0.0,
                            processingTimeMs = 50
                        ),
                        scannedAt = s.scannedAt
                    )
                }

                val payload = GasScanBatchRequest(
                    examId = examId,
                    deviceId = deviceId,
                    scans = scanItems
                )

                val response = apiService.postScans(backendUrl, payload)
                if (response.isSuccessful && response.body()?.success == true) {
                    val body = response.body()!!
                    val nowIso = body.syncedAt ?: isoDateFormat.format(Date())
                    val scanIds = examScans.map { it.scanId }

                    scanDao.markScansAsSynced(scanIds, nowIso)
                    totalSyncedCount += body.inserted
                } else {
                    val errIds = examScans.map { it.scanId }
                    scanDao.markScansAsFailed(errIds)
                    return@withContext Result.failure(
                        Exception("Sync failed: HTTP ${response.code()} ${response.message()} - ${response.body()?.message}")
                    )
                }
            }

            Result.success(totalSyncedCount)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

