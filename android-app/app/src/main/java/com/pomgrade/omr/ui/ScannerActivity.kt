package com.pomgrade.omr.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.pomgrade.omr.OmrApplication
import com.pomgrade.omr.databinding.ActivityScannerBinding
import com.pomgrade.omr.omr.OmrProcessor
import com.pomgrade.omr.sync.SyncWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

/**
 * Native CameraX Real-Time OMR Scanner Activity.
 * Runs two-stage geometric rectification and bubble decoding at camera frame rate.
 *
 * Credit: จัดทำโดย ครูสุวรรณษา เดชรักษา
 */
class ScannerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityScannerBinding
    private val app by lazy { application as OmrApplication }
    private val omrProcessor = OmrProcessor()

    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private var camera: Camera? = null
    private var isFlashOn = false

    private var activeExamId: String = "EXAM_001"
    private var answerKey: List<String> = listOf("A", "B", "C", "D", "A", "B", "C", "D", "A", "B", "C", "D", "A", "B", "C", "D", "A", "B", "C", "D")

    private var sessionScanCount = 0
    private var isProcessingLocked = false
    private var lastScannedStudentId = ""
    private var lastScanTimestamp = 0L

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startCamera()
        } else {
            Toast.makeText(this, "จำเป็นต้องอนุญาตการใช้กล้องเพื่อสแกน", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScannerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        loadSettings()
        setupListeners()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    private fun loadSettings() {
        val prefs = getSharedPreferences("omr_settings", Context.MODE_PRIVATE)
        activeExamId = prefs.getString("exam_id", "EXAM_001") ?: "EXAM_001"
        binding.tvScannerExamId.text = "EXAM: $activeExamId"
    }

    private fun setupListeners() {
        binding.btnClose.setOnClickListener { finish() }

        binding.btnToggleFlash.setOnClickListener {
            camera?.let { cam ->
                if (cam.cameraInfo.hasFlashUnit()) {
                    isFlashOn = !isFlashOn
                    cam.cameraControl.enableTorch(isFlashOn)
                }
            }
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.previewView.surfaceProvider)
            }

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()

            imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                analyzeFrame(imageProxy)
            }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    imageAnalysis
                )
            } catch (exc: Exception) {
                Toast.makeText(this, "เกิดข้อผิดพลาดในการเปิดกล้อง: ${exc.message}", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    @OptIn(ExperimentalGetImage::class)
    private fun analyzeFrame(imageProxy: ImageProxy) {
        if (isProcessingLocked) {
            imageProxy.close()
            return
        }

        val bitmap = imageProxyToBitmap(imageProxy)
        imageProxy.close() ?: return

        if (bitmap == null) return

        val result = omrProcessor.processFrame(
            bitmap = bitmap,
            examId = activeExamId,
            answerKey = answerKey
        )

        runOnUiThread {
            if (result != null) {
                binding.scannerOverlay.updateDetectedCorners(result.corners)

                // Debounce duplicate scans of the same student within 3 seconds
                val now = System.currentTimeMillis()
                if (result.studentId == lastScannedStudentId && (now - lastScanTimestamp) < 3000L) {
                    return@runOnUiThread
                }

                // Lock processing to prevent double recording
                isProcessingLocked = true
                lastScannedStudentId = result.studentId
                lastScanTimestamp = now

                handleSuccessfulScan(result)
            } else {
                binding.scannerOverlay.updateDetectedCorners(null)
            }
        }
    }

    private fun handleSuccessfulScan(result: OmrProcessor.ProcessResult) {
        sessionScanCount++
        binding.tvSessionCount.text = "สแกนในรอบนี้: $sessionScanCount แผ่น"

        // Sound & Haptic Feedback
        triggerFeedback()

        // Show HUD Card
        binding.tvLastStudentId.text = "รหัสนักเรียน: ${result.studentId}"
        binding.tvLastScore.text = "คะแนน: ${result.score} / ${result.totalQuestions} (${result.percentage}%)"
        binding.cardLastResult.visibility = View.VISIBLE

        // Persist to Room Database
        lifecycleScope.launch {
            app.scanRepository.saveScan(result)

            // Trigger background sync to Google Apps Script
            SyncWorker.scheduleSync(applicationContext)

            // Unlock analyzer after 1.5s
            delay(1500)
            isProcessingLocked = false
        }
    }

    private fun triggerFeedback() {
        val prefs = getSharedPreferences("omr_settings", Context.MODE_PRIVATE)
        val soundEnabled = prefs.getBoolean("sound_enabled", true)
        val vibrateEnabled = prefs.getBoolean("vibrate_enabled", true)

        if (soundEnabled) {
            try {
                val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
                tone.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
            } catch (e: Exception) {
                // Ignore tone error
            }
        }

        if (vibrateEnabled) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                    vibratorManager.defaultVibrator.vibrate(
                        VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE)
                    )
                } else {
                    @Suppress("DEPRECATION")
                    val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                    vibrator.vibrate(100)
                }
            } catch (e: Exception) {
                // Ignore vibration error
            }
        }
    }

    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
        val planes = imageProxy.planes
        val buffer = planes[0].buffer
        val pixelStride = planes[0].pixelStride
        val rowStride = planes[0].rowStride
        val rowPadding = rowStride - pixelStride * imageProxy.width

        val bitmap = Bitmap.createBitmap(
            imageProxy.width + rowPadding / pixelStride,
            imageProxy.height,
            Bitmap.Config.ARGB_8888
        )
        bitmap.copyPixelsFromBuffer(buffer)

        // Rotate to match display orientation
        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        if (rotationDegrees != 0) {
            val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
            return Bitmap.createBitmap(bitmap, 0, 0, imageProxy.width, imageProxy.height, matrix, true)
        }

        return Bitmap.createBitmap(bitmap, 0, 0, imageProxy.width, imageProxy.height)
    }
}
