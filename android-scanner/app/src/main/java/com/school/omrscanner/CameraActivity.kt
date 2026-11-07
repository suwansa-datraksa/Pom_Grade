package com.school.omrscanner

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.card.MaterialCardView
import com.school.omrscanner.data.local.AppDatabase
import com.school.omrscanner.data.local.ScanEntity
import com.school.omrscanner.omr.*
import com.school.omrscanner.sync.SyncWorker
import com.school.omrscanner.template.TemplateModel
import com.school.omrscanner.ui.ScannerOverlayView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.Point
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * CameraActivity: CameraX stream with live OpenCV OMR processing.
 * Detects 4 fiducials, warps geometry, reads dual-sided timing tracks,
 * interpolates bubble centroids, evaluates answers & student ID,
 * and persists to Room database.
 *
 * Pom Grade - จัดทำโดย ครูสุวรรณษา เดชรักษา
 */
class CameraActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var scannerOverlay: ScannerOverlayView
    private lateinit var tvScannerExamId: TextView
    private lateinit var tvScannerStatus: TextView
    private lateinit var tvSessionCount: TextView
    private lateinit var cardLastResult: MaterialCardView
    private lateinit var tvLastStudentId: TextView
    private lateinit var tvLastScore: TextView
    private lateinit var btnToggleFlash: ImageButton
    private lateinit var btnClose: ImageButton

    private lateinit var cameraExecutor: ExecutorService
    private var camera: Camera? = null
    private var isFlashOn = false

    private val isProcessing = AtomicBoolean(false)
    private var sessionScannedCount = 0
    private var currentExamId = "EXAM_001"
    private var deviceId = "DEVICE_ANDROID_01"

    private var toneGenerator: ToneGenerator? = null
    private var vibrator: Vibrator? = null

    // Sample default 20-question template coordinate configuration
    private val defaultTemplate = TemplateModel(
        examId = "EXAM_001",
        name = "Pom Grade Standard 20Q",
        questionsCount = 20,
        choicesCount = 4,
        timingLeftBar = TemplateModel.TimingBarSpec(x = 10.0, yStart = 68.0, width = 5.0, height = 1.6, spacingY = 9.0),
        timingRightBar = TemplateModel.TimingBarSpec(x = 195.0, yStart = 68.0, width = 3.0, height = 1.6, spacingY = 9.0),
        studentIdGrid = TemplateModel.StudentIdSpec(xStart = 35.0, yStart = 30.0, digitSpacingX = 8.0, rowSpacingY = 5.5, bubbleRadius = 2.0),
        questions = (1..20).map { q ->
            TemplateModel.QuestionSpec(
                qNumber = q,
                rowY = 68.0 + (q - 1) * 9.0,
                choiceX = listOf(35.0, 47.0, 59.0, 71.0)
            )
        }
    )

    companion object {
        private const val CAMERA_PERMISSION_REQ = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scanner)

        if (!OpenCVLoader.initDebug()) {
            Toast.makeText(this, "OpenCV Initialization Failed", Toast.LENGTH_LONG).show()
        }

        currentExamId = intent.getStringExtra("EXAM_ID") ?: "EXAM_001"
        deviceId = getSharedPreferences("pom_grade_prefs", Context.MODE_PRIVATE)
            .getString("device_id", "DEVICE_ANDROID_01") ?: "DEVICE_ANDROID_01"

        previewView = findViewById(R.id.previewView)
        scannerOverlay = findViewById(R.id.scannerOverlay)
        tvScannerExamId = findViewById(R.id.tvScannerExamId)
        tvScannerStatus = findViewById(R.id.tvScannerStatus)
        tvSessionCount = findViewById(R.id.tvSessionCount)
        cardLastResult = findViewById(R.id.cardLastResult)
        tvLastStudentId = findViewById(R.id.tvLastStudentId)
        tvLastScore = findViewById(R.id.tvLastScore)
        btnToggleFlash = findViewById(R.id.btnToggleFlash)
        btnClose = findViewById(R.id.btnClose)

        tvScannerExamId.text = "EXAM: $currentExamId"
        tvSessionCount.text = "สแกนในรอบนี้: $sessionScannedCount แผ่น"

        cameraExecutor = Executors.newSingleThreadExecutor()

        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
            vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        } catch (_: Exception) {}

        btnClose.setOnClickListener { finish() }

        btnToggleFlash.setOnClickListener {
            toggleFlash()
        }

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                CAMERA_PERMISSION_REQ
            )
        }
    }

    private fun toggleFlash() {
        camera?.let {
            if (it.cameraInfo.hasFlashUnit()) {
                isFlashOn = !isFlashOn
                it.cameraControl.enableTorch(isFlashOn)
                btnToggleFlash.alpha = if (isFlashOn) 1.0f else 0.6f
            }
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor) { imageProxy ->
                        processFrame(imageProxy)
                    }
                }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis)
            } catch (exc: Exception) {
                Toast.makeText(this, "Camera bind error: ${exc.message}", Toast.LENGTH_SHORT).show()
            }

        }, ContextCompat.getMainExecutor(this))
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun processFrame(imageProxy: ImageProxy) {
        if (isProcessing.get()) {
            imageProxy.close()
            return
        }

        val bitmap = imageProxy.toBitmap()
        imageProxy.close()

        val mat = Mat()
        Utils.bitmapToMat(bitmap, mat)

        val markerDetector = MarkerDetector()
        val detectionResult = markerDetector.detectCornersAndOrientation(mat)

        if (detectionResult == null) {
            runOnUiThread {
                scannerOverlay.setState(ScannerOverlayView.State.SEARCHING)
                tvScannerStatus.text = getString(R.string.scanner_align_hint)
            }
            mat.release()
            return
        }

        // We detected all 4 corner fiducials!
        if (!isProcessing.compareAndSet(false, true)) {
            mat.release()
            return
        }

        runOnUiThread {
            scannerOverlay.setState(ScannerOverlayView.State.PROCESSING)
            tvScannerStatus.text = getString(R.string.scanner_processing)
        }

        lifecycleScope.launch(Dispatchers.Default) {
            try {
                // 1. Warp perspective to standard 1000x1414
                val warpedMat = markerDetector.warpToStandard(mat, detectionResult)
                mat.release()

                // 2. Detect Dual-Sided Timing Tracks
                val timingDetector = TimingTrackDetector()
                val timingResult = timingDetector.detectTimingTracks(
                    warpedMat,
                    defaultTemplate.questionsCount,
                    expectedLeftX = 50.0,  // Standard 1000x1414 scale coordinates
                    expectedRightX = 950.0,
                    yStart = 330.0,
                    ySpacing = 43.0
                )

                // 3. Scanline Interpolator
                val interpolator = ScanlineInterpolator(
                    timingResult.leftCentroids,
                    timingResult.rightCentroids
                )

                // 4. Bubble Evaluator
                val evaluator = BubbleEvaluator()

                // Evaluate 5-digit Student ID
                val studentIdResult = evaluator.evaluateStudentIdGrid(
                    warpedMat,
                    xStart = 160.0,
                    yStart = 145.0,
                    digitSpacingX = 38.0,
                    rowSpacingY = 26.0,
                    radius = 9.0
                )

                // Evaluate Questions
                val answerChars = mutableListOf<Char>()
                var uncertainCount = 0

                for (qIdx in 0 until defaultTemplate.questionsCount) {
                    val choiceNormT = listOf(0.18, 0.24, 0.30, 0.36) // normalized x-positions across left/right
                    val rowResult = evaluator.evaluateQuestionRow(
                        warpedMat,
                        interpolator,
                        rowIndex = qIdx,
                        choiceNormT = choiceNormT,
                        radius = 9.0
                    )
                    answerChars.add(rowResult.selectedChoice)
                    if (rowResult.selectedChoice == '?') {
                        uncertainCount++
                    }
                }

                warpedMat.release()

                val encoder = AnswerEncoder()
                val answersRaw = encoder.encode(answerChars)
                val statusFlag = encoder.determineStatusFlag(answersRaw, studentIdResult.isValid)

                // Calculate preliminary score against answer key if known
                val score = calculateScore(answersRaw)
                val maxScore = defaultTemplate.questionsCount.toDouble()

                val nowIso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }.format(Date())

                val scanId = UUID.randomUUID().toString()
                val syncStatus = if (statusFlag == "NEEDS_REVIEW" || !studentIdResult.isValid) 3 else 0

                val scanEntity = ScanEntity(
                    scanId = scanId,
                    scannedAt = nowIso,
                    deviceId = deviceId,
                    examId = currentExamId,
                    studentCode = studentIdResult.studentId,
                    score = score,
                    maxScore = maxScore,
                    answers = answersRaw,
                    status = statusFlag,
                    syncStatus = syncStatus
                )

                // Save to Room DB
                val db = AppDatabase.getInstance(this@CameraActivity)
                db.scanDao().insertScan(scanEntity)

                // Trigger WorkManager sync if pending
                if (syncStatus == 0) {
                    SyncWorker.enqueueImmediateSync(this@CameraActivity)
                }

                withContext(Dispatchers.Main) {
                    sessionScannedCount++
                    tvSessionCount.text = "สแกนในรอบนี้: $sessionScannedCount แผ่น"
                    scannerOverlay.setState(ScannerOverlayView.State.SUCCESS)

                    tvLastStudentId.text = "รหัสนักเรียน: ${studentIdResult.studentId}"
                    tvLastScore.text = "คำตอบ: $answersRaw | สถานะ: $statusFlag"
                    cardLastResult.visibility = View.VISIBLE

                    triggerFeedback()

                    // If needs review, open ReviewActivity
                    if (syncStatus == 3) {
                        Toast.makeText(
                            this@CameraActivity,
                            "พบข้อที่ต้องตรวจสอบเพิ่มเติม (สถานะ: $statusFlag)",
                            Toast.LENGTH_SHORT
                        ).show()
                        val intent = Intent(this@CameraActivity, ReviewActivity::class.java).apply {
                            putExtra("SCAN_ID", scanId)
                        }
                        startActivity(intent)
                    }

                    // Release processing lock after brief delay so teacher can move to next sheet
                    previewView.postDelayed({
                        isProcessing.set(false)
                        scannerOverlay.setState(ScannerOverlayView.State.SEARCHING)
                        tvScannerStatus.text = getString(R.string.scanner_align_hint)
                    }, 1200)
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isProcessing.set(false)
                    scannerOverlay.setState(ScannerOverlayView.State.SEARCHING)
                    tvScannerStatus.text = "เกิดข้อผิดพลาดในการประมวลผล: ${e.message}"
                }
            }
        }
    }

    private fun calculateScore(answers: String): Double {
        // Fallback default answer key: all 'A' or stored preference
        val answerKey = getSharedPreferences("pom_grade_prefs", Context.MODE_PRIVATE)
            .getString("answer_key_$currentExamId", "") ?: ""

        if (answerKey.isEmpty()) {
            return 0.0
        }

        var correct = 0.0
        val len = minOf(answers.length, answerKey.length)
        for (i in 0 until len) {
            if (answers[i] == answerKey[i]) {
                correct += 1.0
            }
        }
        return correct
    }

    private fun triggerFeedback() {
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(100)
            }
        } catch (_: Exception) {}
    }

    private fun allPermissionsGranted() = ContextCompat.checkSelfPermission(
        this, Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_REQ) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this, "จำเป็นต้องอนุญาตการเข้าถึงกล้องเพื่อสแกน", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        toneGenerator?.release()
    }
}
