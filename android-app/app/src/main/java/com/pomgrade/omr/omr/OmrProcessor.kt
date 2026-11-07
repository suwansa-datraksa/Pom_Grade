package com.pomgrade.omr.omr

import android.graphics.Bitmap
import java.util.UUID

/**
 * End-to-end OMR Scanning Pipeline.
 * Executes Stage 1 (Homography) -> Stage 2 (Timing Track Interpolation) -> Bubble Analysis.
 *
 * Credit: จัดทำโดย ครูสุวรรณษา เดชรักษา
 */
class OmrProcessor(
    private val fiducialDetector: FiducialDetector = FiducialDetector(),
    private val geometryRectifier: GeometryRectifier = GeometryRectifier(),
    private val timingInterpolator: TimingTrackInterpolator = TimingTrackInterpolator(),
    private val bubbleAnalyzer: BubbleAnalyzer = BubbleAnalyzer()
) {

    data class ProcessResult(
        val scanId: String,
        val examId: String,
        val studentId: String,
        val score: Int,
        val totalQuestions: Int,
        val percentage: Double,
        val answers: List<String>,
        val confidence: Double,
        val skewAngle: Double,
        val processingTimeMs: Long,
        val corners: GeometryRectifier.QuadCorners
    )

    /**
     * Processes a single camera frame.
     *
     * @param bitmap Camera frame in ARGB_8888 or RGB_565 format
     * @param examId Current active exam ID
     * @param answerKey List of correct answers (e.g. ["A", "B", "C", ...])
     * @return ProcessResult if detected and decoded successfully, or null if sheet not found.
     */
    fun processFrame(
        bitmap: Bitmap,
        examId: String,
        answerKey: List<String>
    ): ProcessResult? {
        val startTime = System.currentTimeMillis()

        // 1. Detect 4 Corner Fiducials
        val detection = fiducialDetector.detectFiducials(bitmap) ?: return null

        // 2. Stage 1: Global 4-Corner Homography Perspective Rectification
        val canonicalGray = geometryRectifier.warpToCanonical(
            srcBitmap = bitmap,
            corners = detection.corners
        )

        // 3. Stage 2: Dual-Sided Timing Track Extraction & Local Interpolation
        val numQuestions = answerKey.size.coerceAtMost(OmrConfig.NUM_ROWS)
        val timingPairs = timingInterpolator.extractRowTimingTracks(
            canonicalGray = canonicalGray,
            numRows = numQuestions
        )

        // 4. Decode Student ID from Header Matrix
        val studentId = bubbleAnalyzer.decodeStudentId(canonicalGray)

        // 5. Decode Answers for each question row
        val detectedAnswers = ArrayList<String>(numQuestions)
        var totalScore = 0
        var sumConfidence = 0.0

        for (qIdx in 0 until numQuestions) {
            val timingPair = timingPairs[qIdx]

            // Calculate precise coordinate for each option using:
            // P_i(t) = (1 - t)*P_L,i + t*P_R,i
            val optionPoints = OmrConfig.CHOICE_T_OFFSETS.map { t ->
                timingInterpolator.evaluateBubbleCoordinate(timingPair, t)
            }

            val qResult = bubbleAnalyzer.decodeQuestionRow(
                canonicalGray = canonicalGray,
                width = OmrConfig.CANONICAL_WIDTH,
                height = OmrConfig.CANONICAL_HEIGHT,
                questionIndex = qIdx,
                optionCoordinates = optionPoints
            )

            detectedAnswers.add(qResult.selectedOption)
            sumConfidence += qResult.confidence

            // Check against answer key
            val correctChoice = answerKey.getOrElse(qIdx) { "" }
            if (qResult.selectedOption != "-" && qResult.selectedOption != "*" &&
                qResult.selectedOption.equals(correctChoice, ignoreCase = true)) {
                totalScore++
            }
        }

        val totalQuestions = numQuestions
        val percentage = if (totalQuestions > 0) {
            Math.round((totalScore.toDouble() / totalQuestions) * 10000.0) / 100.0
        } else {
            0.0
        }

        val meanConfidence = if (numQuestions > 0) sumConfidence / numQuestions else 0.0
        val processDuration = System.currentTimeMillis() - startTime
        val scanUuid = UUID.randomUUID().toString()

        return ProcessResult(
            scanId = scanUuid,
            examId = examId,
            studentId = studentId,
            score = totalScore,
            totalQuestions = totalQuestions,
            percentage = percentage,
            answers = detectedAnswers,
            confidence = meanConfidence,
            skewAngle = detection.skewAngleDegrees,
            processingTimeMs = processDuration,
            corners = detection.corners
        )
    }
}

