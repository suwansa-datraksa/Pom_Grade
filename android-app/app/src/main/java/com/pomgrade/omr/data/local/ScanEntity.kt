package com.pomgrade.omr.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Local Room Entity for OMR Scans.
 * Persists results locally with append-only semantics and UUID deduplication.
 *
 * Credit: จัดทำโดย ครูสุวรรณษา เดชรักษา
 */
@Entity(
    tableName = "scans",
    indices = [
        Index(value = ["scan_id"], unique = true),
        Index(value = ["student_id", "exam_id"]),
        Index(value = ["sync_status"])
    ]
)
data class ScanEntity(
    @PrimaryKey
    @ColumnInfo(name = "scan_id")
    val scanId: String,

    @ColumnInfo(name = "exam_id")
    val examId: String,

    @ColumnInfo(name = "student_id")
    val studentId: String,

    @ColumnInfo(name = "score")
    val score: Int,

    @ColumnInfo(name = "total_questions")
    val totalQuestions: Int,

    @ColumnInfo(name = "percentage")
    val percentage: Double,

    @ColumnInfo(name = "answers_json")
    val answersJson: String,

    @ColumnInfo(name = "metrics_json")
    val metricsJson: String,

    @ColumnInfo(name = "scanned_at")
    val scannedAt: String,

    @ColumnInfo(name = "sync_status")
    val syncStatus: SyncStatus = SyncStatus.PENDING,

    @ColumnInfo(name = "synced_at")
    val syncedAt: String? = null,

    @ColumnInfo(name = "created_timestamp")
    val createdTimestamp: Long = System.currentTimeMillis()
)

enum class SyncStatus {
    PENDING,
    SYNCED,
    FAILED
}

