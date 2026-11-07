package com.pomgrade.omr.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.pomgrade.omr.OmrApplication
import com.pomgrade.omr.R
import com.pomgrade.omr.databinding.ActivityMainBinding
import com.pomgrade.omr.sync.SyncWorker
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Main Dashboard Activity.
 *
 * Credit: จัดทำโดย ครูสุวรรณษา เดชรักษา
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val app by lazy { application as OmrApplication }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        observeStats()
    }

    override fun onResume() {
        super.onResume()
        updateExamAndAuthStatus()
    }

    private fun setupUI() {
        binding.btnStartScan.setOnClickListener {
            startActivity(Intent(this, ScannerActivity::class.java))
        }

        binding.btnViewHistory.setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }

        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        binding.btnSyncNow.setOnClickListener {
            SyncWorker.scheduleSync(this)
            Toast.makeText(this, "เริ่มการซิงค์ข้อมูลกับคลาวด์แล้ว", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateExamAndAuthStatus() {
        val prefs = getSharedPreferences("omr_settings", Context.MODE_PRIVATE)
        val currentExamId = prefs.getString("exam_id", "EXAM_001") ?: "EXAM_001"
        binding.tvCurrentExamId.text = currentExamId

        val account = app.authManager.getSignedInAccount()
        if (account != null) {
            binding.tvAuthStatus.text = getString(R.string.status_signed_in, account.email)
            binding.tvAuthStatus.setBackgroundColor(getColor(R.color.status_success))
        } else {
            binding.tvAuthStatus.text = getString(R.string.status_not_signed_in)
            binding.tvAuthStatus.setBackgroundColor(getColor(R.color.secondary))
        }
    }

    private fun observeStats() {
        lifecycleScope.launch {
            app.scanRepository.totalScansCountFlow.collectLatest { count ->
                binding.tvTotalScansCount.text = count.toString()
            }
        }

        lifecycleScope.launch {
            app.scanRepository.pendingScansCountFlow.collectLatest { count ->
                binding.tvPendingSyncCount.text = count.toString()
            }
        }
    }
}

