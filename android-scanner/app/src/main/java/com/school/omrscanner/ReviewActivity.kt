package com.school.omrscanner

import android.os.Bundle
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.school.omrscanner.data.local.AppDatabase
import com.school.omrscanner.data.local.ScanEntity
import com.school.omrscanner.sync.SyncWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ReviewActivity: Interactive teacher verification screen for ambiguous marks ('?', '*')
 * or missing/unrecognized student IDs. Updates Room database and triggers cloud synchronization.
 *
 * Pom Grade - จัดทำโดย ครูสุวรรณษา เดชรักษา
 */
class ReviewActivity : AppCompatActivity() {

    private lateinit var toolbar: MaterialToolbar
    private lateinit var etStudentId: TextInputEditText
    private lateinit var tvScanMeta: TextView
    private lateinit var llQuestionsContainer: LinearLayout
    private lateinit var btnSaveAndSync: MaterialButton

    private var scanId: String = ""
    private var currentEntity: ScanEntity? = null
    private val questionAnswers = mutableMapOf<Int, Char>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_review)

        scanId = intent.getStringExtra("SCAN_ID") ?: ""

        toolbar = findViewById(R.id.toolbar)
        etStudentId = findViewById(R.id.etStudentId)
        tvScanMeta = findViewById(R.id.tvScanMeta)
        llQuestionsContainer = findViewById(R.id.llQuestionsContainer)
        btnSaveAndSync = findViewById(R.id.btnSaveAndSync)

        toolbar.setNavigationOnClickListener { finish() }

        btnSaveAndSync.setOnClickListener {
            saveAndSync()
        }

        loadScanData()
    }

    private fun loadScanData() {
        if (scanId.isEmpty()) {
            Toast.makeText(this, "ไม่พบรหัสสแกน", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getInstance(this@ReviewActivity)
            val entity = db.scanDao().getScanById(scanId)

            withContext(Dispatchers.Main) {
                if (entity == null) {
                    Toast.makeText(this@ReviewActivity, "ไม่พบข้อมูลการสแกนในระบบ", Toast.LENGTH_SHORT).show()
                    finish()
                    return@withContext
                }

                currentEntity = entity
                etStudentId.setText(entity.studentCode)
                tvScanMeta.text = "รหัสข้อสอบ: ${entity.examId} | เวลาสแกน: ${entity.scannedAt}"

                populateQuestionList(entity.answers)
            }
        }
    }

    private fun populateQuestionList(answersRaw: String) {
        llQuestionsContainer.removeAllViews()
        questionAnswers.clear()

        answersRaw.forEachIndexed { index, char ->
            val qNum = index + 1
            questionAnswers[qNum] = char

            val rowView = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(12, 12, 12, 12)
            }

            val tvQNum = TextView(this).apply {
                text = "ข้อ $qNum:"
                textSize = 15f
                setPadding(0, 0, 16, 0)
                width = 160
            }
            rowView.addView(tvQNum)

            val radioGroup = RadioGroup(this).apply {
                orientation = RadioGroup.HORIZONTAL
            }

            val choices = listOf('A', 'B', 'C', 'D', 'E', '-')
            choices.forEach { choiceChar ->
                val rb = RadioButton(this).apply {
                    text = choiceChar.toString()
                    isChecked = (char == choiceChar)
                    setOnCheckedChangeListener { _, isChecked ->
                        if (isChecked) {
                            questionAnswers[qNum] = choiceChar
                        }
                    }
                }
                radioGroup.addView(rb)
            }

            // Flag uncertain questions with amber or red highlight
            if (char == '?' || char == '*') {
                rowView.setBackgroundColor(android.graphics.Color.parseColor("#FEF3C7")) // light amber
            }

            rowView.addView(radioGroup)
            llQuestionsContainer.addView(rowView)
        }
    }

    private fun saveAndSync() {
        val studentId = etStudentId.text.toString().trim()
        if (studentId.length != 5 || !studentId.all { it.isDigit() }) {
            Toast.makeText(this, "กรุณาระบุรหัสนักเรียนเป็นตัวเลข 5 หลัก", Toast.LENGTH_SHORT).show()
            return
        }

        val updatedAnswers = (1..questionAnswers.size).map { q ->
            questionAnswers[q] ?: '-'
        }.joinToString("")

        // Check if any '?' or '*' remains
        val hasUncertain = updatedAnswers.contains('?') || updatedAnswers.contains('*')
        val statusFlag = if (hasUncertain) "NEEDS_REVIEW" else "OK"

        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getInstance(this@ReviewActivity)
            db.scanDao().updateReview(
                scanId = scanId,
                studentCode = studentId,
                answers = updatedAnswers,
                status = statusFlag,
                syncStatus = if (hasUncertain) 3 else 0 // 0: pending sync, 3: review needed
            )

            if (!hasUncertain) {
                SyncWorker.enqueueImmediateSync(this@ReviewActivity)
            }

            withContext(Dispatchers.Main) {
                Toast.makeText(this@ReviewActivity, "บันทึกข้อมูลเรียบร้อยแล้ว", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }
}
