package com.pomgrade.omr.data.remote

import com.google.gson.annotations.SerializedName

/**
 * Data Transfer Objects for Google Apps Script Cloud Backend communication.
 * Strictly adheres to Architecture v6 Section 6 specification.
 *
 * Credit: จัดทำโดย ครูสุวรรณษา เดชรักษา
 */
data class GasScanBatchRequest(
    @SerializedName("exam_id")
    val examId: String,

    @SerializedName("device_id")
    val deviceId: String,

    @SerializedName("scans")
    val scans: List<GasScanItemRequest>
)

data class GasScanItemRequest(
    @SerializedName("scan_id")
    val scanId: String,

    @SerializedName("student_id")
    val studentId: String,

    @SerializedName("score")
    val score: Int,

    @SerializedName("total_questions")
    val totalQuestions: Int,

    @SerializedName("answers")
    val answers: List<String>,

    @SerializedName("metrics")
    val metrics: GasScanMetrics,

    @SerializedName("scanned_at")
    val scannedAt: String
)

data class GasScanMetrics(
    @SerializedName("confidence")
    val confidence: Double,

    @SerializedName("skew_angle")
    val skewAngle: Double,

    @SerializedName("processing_time_ms")
    val processingTimeMs: Long
)

data class GasScanBatchResponse(
    @SerializedName("success")
    val success: Boolean,

    @SerializedName("exam_id")
    val examId: String? = null,

    @SerializedName("total_received")
    val totalReceived: Int = 0,

    @SerializedName("inserted")
    val inserted: Int = 0,

    @SerializedName("duplicates")
    val duplicates: Int = 0,

    @SerializedName("inserted_scan_ids")
    val insertedScanIds: List<String>? = null,

    @SerializedName("error")
    val error: String? = null,

    @SerializedName("message")
    val message: String? = null,

    @SerializedName("credit")
    val credit: String? = null,

    @SerializedName("synced_at")
    val syncedAt: String? = null
)

