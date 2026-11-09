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
import com.school.omrscanner.template.*
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
        templateId = "pom_default_20q",
        version = "6.0.0",
        examId = "EXAM_001",
        examTitle = "Pom Grade 20Q Exam",
        pageSize = PageSize(210.0, 297.0),
        fiducials = FiducialsGroup(
            topLeft = MarkerCoords(12.0, 12.0, 14.0, 14.0),
            topRight = MarkerCoords(190.0, 12.0, 8.0, 8.0),
            bottomRight = MarkerCoords(190.0, 277.0, 8.0, 8.0),
            bottomLeft = MarkerCoords(12.0, 277.0, 8.0, 8.0)
        ),
        timingTracks = TimingTracksGroup(
            leftBars = (0 until 20).map { r -> TimingBarCoords(r, 16.0, 105.0 + r * 7.8, 5.0, 1.6) },
            rightBars = (0 until 20).map { r -> TimingBarCoords(r, 191.0, 105.0 + r * 7.8, 3.0, 1.6) }
        ),
        studentIdMatrix = StudentIdMatrix(
            digits = 5,
            columns = (0 until 5).map { c ->
                (0..9).map { d -> BubbleCoords(d, 32.0 + c * 7.2, 40.0 + d * 5.4, 2.1) }
            }
        ),
        questions = (1..20).map { q ->
            QuestionTemplate(
                questionNumber = q,
                rowIndex = (q - 1) % 20,
                options = listOf("A", "B", "C", "D").mapIndexed { idx, ch ->
                    OptionCoords(ch, 30.0 + idx * 8.0, 105.0 + ((q - 1) % 20) * 7.8, 2.3, 0.15 + idx * 0.20)
                }
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
        val detectionResult = markerDetector.processFrame(mat)

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
                // 1. Get warped canonical Mat from detection result
                val warpedMat = detectionResult.warpedCanonical
                mat.release()

                // 2. Extract Dual-Sided Timing Tracks
                val timingDetector = TimingTrackDetector()
                val timingResult = timingDetector.extractTimingTracks(warpedMat, defaultTemplate)

                // 3. Scanline Interpolator
                val interpolator = ScanlineInterpolator()

                // 4. Bubble Evaluator
                val evaluator = BubbleEvaluator()

                // Scale factors: mm to canonical pixel (1000 x 1414)
                val scaleX = MarkerDetector.CANONICAL_WIDTH / defaultTemplate.pageSize.widthMm
                val scaleY = MarkerDetector.CANONICAL_HEIGHT / defaultTemplate.pageSize.heightMm

                // Evaluate 5-digit Student ID
                val studentIdCols = defaultTemplate.studentIdMatrix.columns.map { col ->
                    col.map { b -> Point(b.xMm * scaleX, b.yMm * scaleY) }
                }
                val studentIdResult = evaluator.evaluateStudentIdGrid(
                    canonicalGrayMat = warpedMat,
                    columns = studentIdCols
                )

                // Evaluate Questions
                val questionEvals = mutableListOf<BubbleEvaluator.QuestionEvaluation>()
                for (qTemplate in defaultTemplate.questions) {
                    val track = timingResult.tracks.getOrNull(qTemplate.rowIndex)
                    val bubbleCenters = if (track != null) {
                        qTemplate.options.map { opt ->
                            interpolator.interpolate(track, opt.tOffset)
                        }
                    } else {
                        qTemplate.options.map { opt ->
                            Point(opt.xMm * scaleX, opt.yMm * scaleY)
                        }
                    }
                    val qEval = evaluator.evaluateQuestion(
                        canonicalGrayMat = warpedMat,
                        questionNumber = qTemplate.questionNumber,
                        bubbleCenters = bubbleCenters,
                        choiceLabels = qTemplate.options.map { it.choice }
                    )
                    questionEvals.add(qEval)
                }

                warpedMat.release()

                val encoder = AnswerEncoder()
                val encodedResult = encoder.encode(studentIdResult, questionEvals, timingResult)
                val answersRaw = encodedResult.answersString
                val statusFlag = encodedResult.statusFlag

                // Calculate preliminary score against answer key if known
                val score = calculateScore(answersRaw)
                val maxScore = defaultTemplate.questions.size.toDouble()

                val scanId = UUID.randomUUID().toString()
                val syncStatus = if (statusFlag == "NEEDS_REVIEW" || !studentIdResult.isValid) 3 else 0

                val scanEntity = ScanEntity(
                    scanId = scanId,
                    scannedAt = System.currentTimeMillis(),
                    deviceId = deviceId,
                    examId = currentExamId,
                    studentCode = studentIdResult.studentId ?: "00000",
                    score = score,
                    maxScore = maxScore,
                    answers = answersRaw,
                    flag = statusFlag,
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

                    tvLastStudentId.text = "รหัสนักเรียน: ${studentIdResult.studentId ?: "รอตรวจสอบ"}"
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

