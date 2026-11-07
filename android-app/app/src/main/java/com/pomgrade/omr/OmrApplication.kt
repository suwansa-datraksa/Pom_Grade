package com.pomgrade.omr

import android.app.Application
import com.pomgrade.omr.auth.GoogleAuthManager
import com.pomgrade.omr.data.local.AppDatabase
import com.pomgrade.omr.data.remote.AuthInterceptor
import com.pomgrade.omr.data.remote.GasApiService
import com.pomgrade.omr.data.repository.ScanRepository
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Android Application class.
 * Centralizes dependency wiring without third-party DI framework overhead.
 *
 * Credit: จัดทำโดย ครูสุวรรณษา เดชรักษา
 */
class OmrApplication : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var authManager: GoogleAuthManager
        private set

    lateinit var scanRepository: ScanRepository
        private set

    override fun onCreate() {
        super.onCreate()

        // 1. Initialize Room Local Database
        database = AppDatabase.getInstance(this)

        // 2. Initialize Google Auth Manager
        authManager = GoogleAuthManager(this)

        // 3. Configure OkHttp with AuthInterceptor & 302 Redirect handling
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(authManager))
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()

        // 4. Configure Retrofit Service
        val retrofit = Retrofit.Builder()
            .baseUrl("https://script.google.com/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        val apiService = retrofit.create(GasApiService::class.java)

        // 5. Initialize Repository
        scanRepository = ScanRepository(
            scanDao = database.scanDao(),
            apiService = apiService
        )
    }
}
