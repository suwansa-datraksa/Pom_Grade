package com.pomgrade.omr.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Url

/**
 * Retrofit Service for Google Apps Script Web App.
 *
 * Credit: จัดทำโดย ครูสุวรรณษา เดชรักษา
 */
interface GasApiService {

    @POST
    suspend fun postScans(
        @Url endpointUrl: String,
        @Body payload: GasScanBatchRequest
    ): Response<GasScanBatchResponse>
}
