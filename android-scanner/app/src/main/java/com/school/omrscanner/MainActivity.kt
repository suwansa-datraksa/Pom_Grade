package com.school.omrscanner

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.school.omrscanner.auth.GoogleAuthProvider
import com.school.omrscanner.data.local.AppDatabase
import com.school.omrscanner.sync.SyncWorker
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * MainActivity: Main dashboard for Pom Grade.
 * Displays scan counts, pending cloud syncs, auth status,
 * and controls for starting scans, sync, history, and settings.
 *
 * Pom Grade - จัดทำโดย ครูสุวรรณษา เดชรักษา
 */
class MainActivity : AppCompatActivity() {

    private lateinit var tvAuthStatus: TextView
    private lateinit var tvCurrentExamId: TextView
    private lateinit var tvTotalScansCount: TextView
    private lateinit var tvPendingSyncCount: TextView
    private lateinit var btnStartScan: MaterialButton
    private lateinit var btnSyncNow: MaterialButton
    private lateinit var btnViewHistory: MaterialButton
    private lateinit var btnSettings: MaterialButton

    private lateinit var authProvider: GoogleAuthProvider

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        authProvider = GoogleAuthProvider(this)

        tvAuthStatus = findViewById(R.id.tvAuthStatus)
        tvCurrentExamId = findViewById(R.id.tvCurrentExamId)
        tvTotalScansCount = findViewById(R.id.tvTotalScansCount)
        tvPendingSyncCount = findViewById(R.id.tvPendingSyncCount)
        btnStartScan = findViewById(R.id.btnStartScan)
        btnSyncNow = findViewById(R.id.btnSyncNow)
        btnViewHistory = findViewById(R.id.btnViewHistory)
        btnSettings = findViewById(R.id.btnSettings)

        setupListeners()
        observeDatabaseCounts()
    }

    override fun onResume() {
        super.onResume()
        updatePreferencesAndAuth()
    }

    private fun setupListeners() {
        btnStartScan.setOnClickListener {
            val examId = tvCurrentExamId.text.toString()
            val intent = Intent(this, CameraActivity::class.java).apply {
                putExtra("EXAM_ID", examId)
            }
            startActivity(intent)
        }

        btnSyncNow.setOnClickListener {
            Toast.makeText(this, "กำลังเริ่มการซิงค์ข้อมูลกับ Google Sheets…", Toast.LENGTH_SHORT).show()
            SyncWorker.enqueueImmediateSync(this)
        }

        btnViewHistory.setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }

        btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        tvAuthStatus.setOnClickListener {
            if (!authProvider.isSignedIn()) {
                val intent = authProvider.getSignInIntent()
                startActivity(intent)
            }
        }
    }

    private fun updatePreferencesAndAuth() {
        val prefs = getSharedPreferences("pom_grade_prefs", Context.MODE_PRIVATE)
        val examId = prefs.getString("current_exam_id", "EXAM_001") ?: "EXAM_001"
        tvCurrentExamId.text = examId

        if (authProvider.isSignedIn()) {
            val email = authProvider.getCurrentAccountEmail() ?: "เชื่อมต่อแล้ว"
            tvAuthStatus.text = "✓ บัญชี Google: $email"
            tvAuthStatus.setBackgroundColor(android.graphics.Color.parseColor("#10B981")) // Green
        } else {
            tvAuthStatus.text = "แตะเพื่อลงชื่อเข้าใช้ Google (OAuth)"
            tvAuthStatus.setBackgroundColor(android.graphics.Color.parseColor("#2E4A9E"))
        }
    }

    private fun observeDatabaseCounts() {
        val db = AppDatabase.getInstance(this)

        lifecycleScope.launch {
            db.scanDao().observeTotalCount().collectLatest { total ->
                tvTotalScansCount.text = total.toString()
            }
        }

        lifecycleScope.launch {
            db.scanDao().observePendingCount().collectLatest { pending ->
                tvPendingSyncCount.text = pending.toString()
            }
        }
    }
}
