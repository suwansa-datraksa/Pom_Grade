package com.school.omrscanner.omr

import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.core.Rect
import org.opencv.imgproc.Imgproc
import kotlin.math.roundToInt

/**
 * Bubble Fill Evaluation Engine according to Architecture v6 Decision Rules.
 *
 * Question Evaluation Rules (f1 = max fill, f2 = 2nd max fill, delta = f1 - f2):
 *  1. f1 < 0.20                       -> '-' (BLANK / Unanswered)
 *  2. f2 >= 0.35 && delta < 0.25      -> '*' (MULTIPLE marks)
 *  3. f1 >= 0.45 || delta >= 0.25     -> Choice with f1 (VALID mark, overcomes faint pencil erasure)
 *  4. Otherwise                       -> '?' (UNCERTAIN -> NEEDS_REVIEW)
 *
 * Student ID Rules (5 Digits, 0-9 each):
 *  - Each column requires exactly 1 valid mark with f >= 0.45 and delta >= 0.25.
 *  - Missing or ambiguous columns return null / flag for manual review.
 *
 * Credit: จัดทำโดย ครูสุวรรณษา เดชรักษา
 */
class BubbleEvaluator {

    companion object {
        const val THRESHOLD_BLANK = 0.20
        const val THRESHOLD_MULTIPLE_F2 = 0.35
        const val THRESHOLD_VALID_F1 = 0.45
        const val THRESHOLD_DELTA = 0.25
        const val DEFAULT_BUBBLE_RADIUS_PX = 14.0
    }

    data class BubbleMetric(
        val center: Point,
        val fillRatio: Double,
        val darkPixelsCount: Int,
        val totalMaskPixels: Int
    )

    data class QuestionEvaluation(
        val questionNumber: Int,
        val recognizedAnswer: String, // "A", "B", "C", "D", "E", "-", "*", "?"
        val fillRatios: List<Double>,
        val f1: Double,
        val f2: Double,
        val delta: Double,
        val isCertain: Boolean
    )

    data class StudentIdEvaluation(
        val studentId: String?,
        val isValid: Boolean,
        val perColumnDigits: List<Int?>,
        val perColumnCertainty: List<Boolean>,
        val rawValues: List<List<Double>>
    )

    /**
     * Calculates the fill ratio of a circular bubble at specified (x, y) coordinates.
     */
    fun evaluateBubbleFill(
        canonicalGrayMat: Mat,
        center: Point,
        radiusPx: Double = DEFAULT_BUBBLE_RADIUS_PX
    ): BubbleMetric {
        val cx = center.x.roundToInt()
        val cy = center.y.roundToInt()
        val r = radiusPx.roundToInt()
        val rSq = radiusPx * radiusPx

        val minX = (cx - r).coerceIn(0, canonicalGrayMat.width() - 1)
        val maxX = (cx + r).coerceIn(0, canonicalGrayMat.width() - 1)
        val minY = (cy - r).coerceIn(0, canonicalGrayMat.height() - 1)
        val maxY = (cy + r).coerceIn(0, canonicalGrayMat.height() - 1)

        val roiW = maxX - minX + 1
        val roiH = maxY - minY + 1
        if (roiW <= 0 || roiH <= 0) {
            return BubbleMetric(center, 0.0, 0, 0)
        }

        val sub = canonicalGrayMat.submat(Rect(minX, minY, roiW, roiH))
        val buffer = ByteArray(roiW * roiH)
        sub.get(0, 0, buffer)
        sub.release()

        var totalMask = 0
        var darkCount = 0

        // Darkness threshold (Adaptive local benchmark)
        val darknessThreshold = 140

        for (y in 0 until roiH) {
            val dy = (minY + y) - cy
            val dySq = dy * dy
            val rowOffset = y * roiW

            for (x in 0 until roiW) {
                val dx = (minX + x) - cx
                if (dx * dx + dySq <= rSq) {
                    totalMask++
                    val luma = buffer[rowOffset + x].toInt() and 0xFF
                    if (luma < darknessThreshold) {
                        darkCount++
                    }
                }
            }
        }

        val fillRatio = if (totalMask > 0) darkCount.toDouble() / totalMask else 0.0
        return BubbleMetric(center, fillRatio, darkCount, totalMask)
    }

    /**
     * Decodes a question row following the 4 Decision Rules.
     */
    fun evaluateQuestion(
        canonicalGrayMat: Mat,
        questionNumber: Int,
        bubbleCenters: List<Point>,
        choiceLabels: List<String> = listOf("A", "B", "C", "D"),
        radiusPx: Double = DEFAULT_BUBBLE_RADIUS_PX
    ): QuestionEvaluation {
        val metrics = bubbleCenters.map { evaluateBubbleFill(canonicalGrayMat, it, radiusPx) }
        val fillRatios = metrics.map { it.fillRatio }

        // Find f1 (max) and f2 (2nd max)
        var maxIdx = -1
        var f1 = -1.0
        var f2 = -1.0

        for (i in fillRatios.indices) {
            val r = fillRatios[i]
            if (r > f1) {
                f2 = f1
                f1 = r
                maxIdx = i
            } else if (r > f2) {
                f2 = r
            }
        }

        val delta = (f1 - f2).coerceAtLeast(0.0)

        // Decision Rules:
        val (ans, isCertain) = when {
            // Rule 1: Blank / Unanswered
            f1 < THRESHOLD_BLANK -> Pair("-", true)

            // Rule 2: Multiple marks
            f2 >= THRESHOLD_MULTIPLE_F2 && delta < THRESHOLD_DELTA -> Pair("*", false)

            // Rule 3: Valid single mark (or overcoming faint eraser trace)
            f1 >= THRESHOLD_VALID_F1 || delta >= THRESHOLD_DELTA -> Pair(choiceLabels.getOrElse(maxIdx) { "?" }, true)

            // Rule 4: Uncertain / Flag for review
            else -> Pair("?", false)
        }

        return QuestionEvaluation(
            questionNumber = questionNumber,
            recognizedAnswer = ans,
            fillRatios = fillRatios,
            f1 = f1,
            f2 = f2,
            delta = delta,
            isCertain = isCertain
        )
    }

    /**
     * Evaluates a 5-digit Student ID grid requiring exactly 1 valid mark per column.
     */
    fun evaluateStudentIdGrid(
        canonicalGrayMat: Mat,
        columns: List<List<Point>>, // 5 columns, 10 bubbles each
        radiusPx: Double = DEFAULT_BUBBLE_RADIUS_PX * 0.90
    ): StudentIdEvaluation {
        require(columns.size == 5) { "Student ID matrix must have 5 columns, got ${columns.size}" }

        val detectedDigits = mutableListOf<Int?>()
        val columnCertainty = mutableListOf<Boolean>()
        val allRatios = mutableListOf<List<Double>>()

        for (colIdx in columns.indices) {
            val colCenters = columns[colIdx]
            val metrics = colCenters.map { evaluateBubbleFill(canonicalGrayMat, it, radiusPx) }
            val ratios = metrics.map { it.fillRatio }
            allRatios.add(ratios)

            var bestDigit = -1
            var f1 = -1.0
            var f2 = -1.0

            for (d in 0..9) {
                val r = ratios.getOrElse(d) { 0.0 }
                if (r > f1) {
                    f2 = f1
                    f1 = r
                    bestDigit = d
                } else if (r > f2) {
                    f2 = r
                }
            }

            val delta = (f1 - f2).coerceAtLeast(0.0)

            // Requirement 7: Exactly 1 valid mark per column (f1 >= 0.45 and delta >= 0.25)
            if (f1 >= THRESHOLD_VALID_F1 && delta >= THRESHOLD_DELTA) {
                detectedDigits.add(bestDigit)
                columnCertainty.add(true)
            } else {
                detectedDigits.add(null)
                columnCertainty.add(false)
            }
        }

        val allValid = detectedDigits.all { it != null }
        val idString = if (allValid) detectedDigits.joinToString("") else null

        return StudentIdEvaluation(
            studentId = idString,
            isValid = allValid,
            perColumnDigits = detectedDigits,
            perColumnCertainty = columnCertainty,
            rawValues = allRatios
        )
    }
}

