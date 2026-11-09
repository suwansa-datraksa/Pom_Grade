package com.school.omrscanner.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room Entity for local OMR scan records.
 *
 * Sync Status Codes:
 *  0: PENDING (Waiting for network sync)
 *  1: SYNCED (Successfully committed to Google Apps Script Raw_Scans)
 *  2: REJECTED (Rejected by backend validation)
 *  3: REVIEW_NEEDED (Contains '?' or ambiguous student ID, requiring teacher confirmation)
 *
 * Credit: จัดทำโดย ครูสุวรรณษา เดชรักษา
 */
@Entity(
    tableName = "scans",
    indices = [
        Index(value = ["scan_id"], unique = true),
        Index(value = ["exam_id", "student_code"]),
        Index(value = ["sync_status"])
    ]
)
data class ScanEntity(
    @PrimaryKey
    @ColumnInfo(name = "scan_id")
    val scanId: String,

    @ColumnInfo(name = "scanned_at")
    val scannedAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "received_at")
    val receivedAt: String? = null,

    @ColumnInfo(name = "device_id")
    val deviceId: String,

    @ColumnInfo(name = "exam_id")
    val examId: String,

    @ColumnInfo(name = "student_code")
    val studentCode: String,

    @ColumnInfo(name = "score")
    val score: Double = 0.0,

    @ColumnInfo(name = "max_score")
    val maxScore: Double = 0.0,

    @ColumnInfo(name = "answers")
    val answers: String,

    @ColumnInfo(name = "sync_status")
    val syncStatus: Int = STATUS_PENDING,

    @ColumnInfo(name = "flag")
    val flag: String = "OK",

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
) {
    val status: String
        get() = flag

    companion object {
        const val STATUS_PENDING = 0
        const val STATUS_SYNCED = 1
        const val STATUS_REJECTED = 2
        const val STATUS_REVIEW_NEEDED = 3
    }
}
