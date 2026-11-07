package com.school.omrscanner

import android.content.Context
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.school.omrscanner.auth.GoogleAuthProvider

/**
 * SettingsActivity: Allows configuring Exam ID, Google Apps Script backend URL,
 * Device ID, and sensory feedback toggles.
 *
 * Pom Grade - จัดทำโดย ครูสุวรรณษา เดชรักษา
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var toolbar: MaterialToolbar
    private lateinit var tvSettingsAuthEmail: TextView
    private lateinit var btnAuthGoogle: MaterialButton
    private lateinit var etExamId: TextInputEditText
    private lateinit var etBackendUrl: TextInputEditText
    private lateinit var etDeviceId: TextInputEditText
    private lateinit var switchSound: SwitchMaterial
    private lateinit var switchVibrate: SwitchMaterial
    private lateinit var btnSaveSettings: MaterialButton

    private lateinit var authProvider: GoogleAuthProvider

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        authProvider = GoogleAuthProvider(this)

        toolbar = findViewById(R.id.toolbar)
        tvSettingsAuthEmail = findViewById(R.id.tvSettingsAuthEmail)
        btnAuthGoogle = findViewById(R.id.btnAuthGoogle)
        etExamId = findViewById(R.id.etExamId)
        etBackendUrl = findViewById(R.id.etBackendUrl)
        etDeviceId = findViewById(R.id.etDeviceId)
        switchSound = findViewById(R.id.switchSound)
        switchVibrate = findViewById(R.id.switchVibrate)
        btnSaveSettings = findViewById(R.id.btnSaveSettings)

        toolbar.setNavigationOnClickListener { finish() }

        loadSettings()

        btnAuthGoogle.setOnClickListener {
            if (!authProvider.isSignedIn()) {
                startActivity(authProvider.getSignInIntent())
            } else {
                Toast.makeText(this, "เข้าสู่ระบบแล้ว: ${authProvider.getCurrentAccountEmail()}", Toast.LENGTH_SHORT).show()
            }
        }

        btnSaveSettings.setOnClickListener {
            saveSettings()
        }
    }

    override fun onResume() {
        super.onResume()
        updateAuthDisplay()
    }

    private fun updateAuthDisplay() {
        if (authProvider.isSignedIn()) {
            val email = authProvider.getCurrentAccountEmail() ?: "เชื่อมต่อแล้ว"
            tvSettingsAuthEmail.text = "เข้าสู่ระบบแล้ว: $email"
            btnAuthGoogle.text = "เปลี่ยนบัญชี Google"
        } else {
            tvSettingsAuthEmail.text = getString(R.string.status_not_signed_in)
            btnAuthGoogle.text = getString(R.string.btn_sign_in_google)
        }
    }

    private fun loadSettings() {
        val prefs = getSharedPreferences("pom_grade_prefs", Context.MODE_PRIVATE)
        etExamId.setText(prefs.getString("current_exam_id", "EXAM_001"))
        etBackendUrl.setText(prefs.getString("gas_backend_url", "https://script.google.com/macros/s/AKfycbyw.../exec"))
        etDeviceId.setText(prefs.getString("device_id", "DEVICE_ANDROID_01"))
        switchSound.isChecked = prefs.getBoolean("feedback_sound", true)
        switchVibrate.isChecked = prefs.getBoolean("feedback_vibrate", true)
        updateAuthDisplay()
    }

    private fun saveSettings() {
        val prefs = getSharedPreferences("pom_grade_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("current_exam_id", etExamId.text.toString().trim())
            .putString("gas_backend_url", etBackendUrl.text.toString().trim())
            .putString("device_id", etDeviceId.text.toString().trim())
            .putBoolean("feedback_sound", switchSound.isChecked)
            .putBoolean("feedback_vibrate", switchVibrate.isChecked)
            .apply()

        Toast.makeText(this, "บันทึกการตั้งค่าเรียบร้อยแล้ว", Toast.LENGTH_SHORT).show()
        finish()
    }
}
