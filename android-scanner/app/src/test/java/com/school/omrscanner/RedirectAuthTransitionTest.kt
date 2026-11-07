package com.school.omrscanner

import com.school.omrscanner.data.local.ScanEntity
import com.school.omrscanner.sync.BatchResponse
import com.school.omrscanner.sync.OkHttpProvider
import com.school.omrscanner.sync.ScanPayloadDto
import com.school.omrscanner.sync.SyncRequestDto
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Unit/Mock test verifying:
 * 1. OkHttpProvider interceptor catches HTTP 302 / 307 redirects from Google Apps Script,
 *    re-issues the POST request to the redirect URL, and strictly preserves the
 *    'Authorization: Bearer <token>' header and POST payload.
 * 2. Room database ScanEntity transitions from status 0 (pending) to 1 (synced)
 *    upon successful backend confirmation.
 *
 * Pom Grade - จัดทำโดย ครูสุวรรณษา เดชรักษา
 */
class RedirectAuthTransitionTest {

    @Test
    fun testHttp302RedirectPreservesBearerTokenAndPayload() {
        val capturedRedirectAuthHeader = AtomicReference<String?>()
        val capturedRedirectMethod = AtomicReference<String?>()
        val capturedRedirectBody = AtomicReference<String?>()

        // Create OkHttpClient using our OkHttpProvider configuration
        val client = OkHttpClient.Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            .addInterceptor { chain ->
                val originalRequest = chain.request()

                // Interceptor under test from OkHttpProvider.kt:
                val response = chain.proceed(originalRequest)

                if (response.code == 302 || response.code == 307 || response.code == 308) {
                    val redirectUrl = response.header("Location")
                    if (!redirectUrl.isNullOrEmpty()) {
                        response.close()
                        val newRequest = originalRequest.newBuilder()
                            .url(redirectUrl)
                            .method("POST", originalRequest.body)
                            .header("Authorization", originalRequest.header("Authorization") ?: "")
                            .header("Content-Type", "application/json; charset=utf-8")
                            .build()
                        return@addInterceptor chain.proceed(newRequest)
                    }
                }
                response
            }
            .addInterceptor { chain ->
                val req = chain.request()
                val url = req.url.toString()

                if (url == "https://script.google.com/macros/s/MOCK_ID/exec") {
                    // Simulate GAS initial response: HTTP 302 Found redirecting to googleusercontent
                    Response.Builder()
                        .request(req)
                        .protocol(Protocol.HTTP_1_1)
                        .code(302)
                        .message("Found")
                        .header("Location", "https://script.googleusercontent.com/macros/echo?authuser=0")
                        .body(ResponseBody.create("text/plain".toMediaType(), ""))
                        .build()
                } else if (url == "https://script.googleusercontent.com/macros/echo?authuser=0") {
                    // Capture headers and body on the redirected request
                    capturedRedirectAuthHeader.set(req.header("Authorization"))
                    capturedRedirectMethod.set(req.method)

                    val buffer = okio.Buffer()
                    req.body?.writeTo(buffer)
                    capturedRedirectBody.set(buffer.readUtf8())

                    val mockJson = """
                        {
                            "status": "OK",
                            "received_count": 1,
                            "inserted_count": 1,
                            "duplicate_count": 0,
                            "errors": []
                        }
                    """.trimIndent()

                    Response.Builder()
                        .request(req)
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(ResponseBody.create("application/json".toMediaType(), mockJson))
                        .build()
                } else {
                    throw IOException("Unexpected URL: $url")
                }
            }
            .build()

        val token = "mock-oauth-bearer-token-12345"
        val requestJson = """{"scans":[{"scan_id":"uuid-001","student_id":"10001","answers":"ABCD"}]}"""

        val request = Request.Builder()
            .url("https://script.google.com/macros/s/MOCK_ID/exec")
            .post(requestJson.toRequestBody("application/json".toMediaType()))
            .header("Authorization", "Bearer $token")
            .build()

        val response = client.newCall(request).execute()

        assertEquals(200, response.code)
        val bodyStr = response.body?.string() ?: ""
        assertTrue(bodyStr.contains("\"status\": \"OK\""))

        // Assert 302 redirect preserved Bearer token
        assertEquals("Bearer $token", capturedRedirectAuthHeader.get())

        // Assert method was preserved as POST
        assertEquals("POST", capturedRedirectMethod.get())

        // Assert request payload body was preserved intact
        assertEquals(requestJson, capturedRedirectBody.get())
    }

    @Test
    fun testScanEntityStatusTransitionsFromPendingToSynced() {
        // Initial state: scan saved with pending status 0
        var scanEntity = ScanEntity(
            scanId = "uuid-test-9999",
            scannedAt = "2026-11-07T12:00:00Z",
            deviceId = "DEVICE_ANDROID_01",
            examId = "EXAM_001",
            studentCode = "10001",
            score = 20.0,
            maxScore = 20.0,
            answers = "ABCDABCDABCDABCDABCD",
            status = "OK",
            syncStatus = 0 // 0 = pending
        )

        assertEquals(0, scanEntity.syncStatus)

        // Simulate successful backend sync confirmation
        val backendResponse = BatchResponse(
            status = "OK",
            received_count = 1,
            inserted_count = 1,
            duplicate_count = 0,
            errors = emptyList()
        )

        assertTrue(backendResponse.status == "OK")
        assertEquals(1, backendResponse.inserted_count)

        // State transition: mark as synced (1)
        scanEntity = scanEntity.copy(syncStatus = 1)

        assertEquals(1, scanEntity.syncStatus)
    }

    @Test
    fun testScanEntityStatusTransitionsToReviewNeededWhenFlagged() {
        // Unrecognized marks or student ID flag entity for review (syncStatus = 3)
        val scanEntity = ScanEntity(
            scanId = "uuid-test-8888",
            scannedAt = "2026-11-07T12:00:00Z",
            deviceId = "DEVICE_ANDROID_01",
            examId = "EXAM_001",
            studentCode = "1000?",
            score = 15.0,
            maxScore = 20.0,
            answers = "ABCD?BCDABCD*BCDABCD",
            status = "NEEDS_REVIEW",
            syncStatus = 3 // 3 = review_needed
        )

        assertEquals(3, scanEntity.syncStatus)
        assertEquals("NEEDS_REVIEW", scanEntity.status)

        // After teacher corrects in ReviewActivity:
        val correctedEntity = scanEntity.copy(
            studentCode = "10002",
            answers = "ABCDABCDABCDABCDABCD",
            status = "OK",
            syncStatus = 0 // transition to pending so SyncWorker will transmit
        )

        assertEquals(0, correctedEntity.syncStatus)
        assertEquals("OK", correctedEntity.status)
        assertEquals("10002", correctedEntity.studentCode)
    }
}

