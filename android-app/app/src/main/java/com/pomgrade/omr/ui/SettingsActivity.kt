package com.pomgrade.omr.ui

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.pomgrade.omr.OmrApplication
import com.pomgrade.omr.R
import com.pomgrade.omr.databinding.ActivitySettingsBinding
import kotlinx.coroutines.launch

/**
 * System Settings & Google OAuth Authentication Activity.
 *
 * Credit: จัดทำโดย ครูสุวรรณษา เดชรักษา
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private val app by lazy { application as OmrApplication }

    private val signInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.result
                Toast.makeText(this, "ลงชื่อเข้าใช้ด้วย ${account.email} สำเร็จ", Toast.LENGTH_SHORT).show()
                updateAuthStatus()

                // Fetch fresh token in background
                lifecycleScope.launch {
                    val tokenResult = app.authManager.fetchFreshAccessToken()
                    if (tokenResult.isSuccess) {
                        Toast.makeText(this@SettingsActivity, "ได้รับ OAuth Token สำเร็จ", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(this, "เข้าสู่ระบบไม่สำเร็จ: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }

        loadSettings()
        setupListeners()
        updateAuthStatus()
    }

    private fun loadSettings() {
        val prefs = getSharedPreferences("omr_settings", Context.MODE_PRIVATE)
        val defaultDeviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID) ?: "Android_OMR_Scanner"

        binding.etExamId.setText(prefs.getString("exam_id", "EXAM_001"))
        binding.etBackendUrl.setText(prefs.getString("backend_url", "https://script.google.com/macros/s/AKfycbx_SAMPLE/exec"))
        binding.etDeviceId.setText(prefs.getString("device_id", defaultDeviceId))
        binding.switchSound.isChecked = prefs.getBoolean("sound_enabled", true)
        binding.switchVibrate.isChecked = prefs.getBoolean("vibrate_enabled", true)
    }

    private fun setupListeners() {
        binding.btnAuthGoogle.setOnClickListener {
            if (app.authManager.isUserSignedIn()) {
                app.authManager.clearAuth()
                Toast.makeText(this, "ออกจากระบบแล้ว", Toast.LENGTH_SHORT).show()
                updateAuthStatus()
            } else {
                val signInIntent = app.authManager.signInClient.signInIntent
                signInLauncher.launch(signInIntent)
            }
        }

        binding.btnSaveSettings.setOnClickListener {
            val examId = binding.etExamId.text.toString().trim()
            val backendUrl = binding.etBackendUrl.text.toString().trim()
            val deviceId = binding.etDeviceId.text.toString().trim()
            val sound = binding.switchSound.isChecked
            val vibrate = binding.switchVibrate.isChecked

            if (examId.isBlank()) {
                Toast.makeText(this, "กรุณาระบุรหัสข้อสอบ (Exam ID)", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val prefs = getSharedPreferences("omr_settings", Context.MODE_PRIVATE)
            prefs.edit()
                .putString("exam_id", examId)
                .putString("backend_url", backendUrl)
                .putString("device_id", deviceId)
                .putBoolean("sound_enabled", sound)
                .putBoolean("vibrate_enabled", vibrate)
                .apply()

            Toast.makeText(this, "บันทึกการตั้งค่าเรียบร้อยแล้ว", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun updateAuthStatus() {
        val account = app.authManager.getSignedInAccount()
        if (account != null) {
            binding.tvSettingsAuthEmail.text = getString(R.string.status_signed_in, account.email)
            binding.btnAuthGoogle.text = "ออกจากระบบ Google Account"
        } else {
            binding.tvSettingsAuthEmail.text = getString(R.string.status_not_signed_in)
            binding.btnAuthGoogle.text = getString(R.string.btn_sign_in_google)
        }
    }
}

