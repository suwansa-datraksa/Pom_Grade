package com.school.omrscanner.sync

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Url

/**
 * Retrofit Service for Google Apps Script Web App ingestion.
 * Strictly adheres to Architecture Specification v6 Section 6.
 *
 * Credit: จัดทำโดย ครูสุวรรณษา เดชรักษา
 */
interface GasApiService {

    @POST
    suspend fun postScanRecords(
        @Url endpointUrl: String,
        @Body payload: GasRecordsBatchPayload
    ): Response<GasBatchResponse>
}

data class GasRecordsBatchPayload(
    @SerializedName("records")
    val records: List<GasScanRecordItem>
)

data class GasScanRecordItem(
    @SerializedName("scan_id")
    val scanId: String,

    @SerializedName("exam_id")
    val examId: String,

    @SerializedName("student_code")
    val studentCode: String,

    @SerializedName("device_id")
    val deviceId: String,

    @SerializedName("ans")
    val ans: String,

    @SerializedName("scanned_at")
    val scannedAt: Long
)

data class GasBatchResponse(
    @SerializedName("status")
    val status: String, // "SUCCESS", "BUSY", "BAD_REQUEST", "ERROR"

    @SerializedName("message")
    val message: String? = null,

    @SerializedName("results")
    val results: List<GasScanResultItem>? = null,

    @SerializedName("credit")
    val credit: String? = null
)

data class GasScanResultItem(
    @SerializedName("scan_id")
    val scanId: String,

    @SerializedName("result")
    val result: String, // "ACCEPTED", "DUPLICATE", "REJECTED"

    @SerializedName("reason")
    val reason: String? = null,

    @SerializedName("score")
    val score: Int? = null,

    @SerializedName("max_score")
    val maxScore: Int? = null,

    @SerializedName("flag")
    val flag: String? = null
)
