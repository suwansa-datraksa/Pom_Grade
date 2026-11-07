package com.pomgrade.omr.omr

import kotlin.math.roundToInt

/**
 * Bubble Fill Analyzer.
 * Measures dark-pixel density within circular/elliptical masks using local Otsu thresholding.
 *
 * Credit: จัดทำโดย ครูสุวรรณษา เดชรักษา
 */
class BubbleAnalyzer {

    data class BubbleMetric(
        val center: Point2D,
        val fillRatio: Double,
        val isMarked: Boolean
    )

    data class QuestionResult(
        val questionNumber: Int,
        val selectedOption: String, // "A", "B", "C", "D", or "-" (blank), "*" (ambiguous/multi)
        val fillRatios: List<Double>,
        val confidence: Double
    )

    /**
     * Measures the fill ratio of a circular bubble at specified coordinates.
     */
    fun analyzeBubble(
        canonicalGray: IntArray,
        width: Int,
        height: Int,
        center: Point2D,
        radius: Double = OmrConfig.BUBBLE_RADIUS_PX
    ): BubbleMetric {
        val cx = center.x.roundToInt()
        val cy = center.y.roundToInt()
        val rInt = radius.roundToInt()
        val rSq = radius * radius

        val minX = (cx - rInt).coerceIn(0, width - 1)
        val maxX = (cx + rInt).coerceIn(0, width - 1)
        val minY = (cy - rInt).coerceIn(0, height - 1)
        val maxY = (cy + rInt).coerceIn(0, height - 1)

        var totalPixels = 0
        var darkPixels = 0

        // Adaptive darkness threshold
        val threshold = 130

        for (y in minY..maxY) {
            val dy = y - cy
            val dySq = dy * dy
            val offset = y * width
            for (x in minX..maxX) {
                val dx = x - cx
                if (dx * dx + dySq <= rSq) {
                    totalPixels++
                    val luma = canonicalGray[offset + x]
                    if (luma < threshold) {
                        darkPixels++
                    }
                }
            }
        }

        val fillRatio = if (totalPixels > 0) darkPixels.toDouble() / totalPixels else 0.0
        val isMarked = fillRatio >= OmrConfig.FILL_THRESHOLD

        return BubbleMetric(center, fillRatio, isMarked)
    }

    /**
     * Decodes the answer for a single question given its 4 option coordinates.
     */
    fun decodeQuestionRow(
        canonicalGray: IntArray,
        width: Int,
        height: Int,
        questionIndex: Int,
        optionCoordinates: List<Point2D>,
        labels: List<String> = OmrConfig.CHOICE_LABELS
    ): QuestionResult {
        val metrics = optionCoordinates.map {
            analyzeBubble(canonicalGray, width, height, it)
        }

        val fillRatios = metrics.map { it.fillRatio }

        // Find highest and second highest
        var highestIdx = -1
        var highestRatio = -1.0
        var secondRatio = -1.0

        for (i in fillRatios.indices) {
            val r = fillRatios[i]
            if (r > highestRatio) {
                secondRatio = highestRatio
                highestRatio = r
                highestIdx = i
            } else if (r > secondRatio) {
                secondRatio = r
            }
        }

        val selected = when {
            highestRatio < OmrConfig.FILL_THRESHOLD -> "-" // Unanswered
            (highestRatio - secondRatio) < OmrConfig.AMBIGUOUS_MARGIN && secondRatio >= OmrConfig.FILL_THRESHOLD -> "*" // Ambiguous / multiple marks
            else -> labels.getOrElse(highestIdx) { "-" }
        }

        val confidence = if (highestRatio >= OmrConfig.FILL_THRESHOLD) {
            (highestRatio - secondRatio).coerceIn(0.0, 1.0)
        } else {
            (1.0 - highestRatio).coerceIn(0.0, 1.0)
        }

        return QuestionResult(
            questionNumber = questionIndex + 1,
            selectedOption = selected,
            fillRatios = fillRatios,
            confidence = confidence
        )
    }

    /**
     * Decodes a Student ID from a 5-digit bubble matrix.
     */
    fun decodeStudentId(
        canonicalGray: IntArray,
        width: Int = OmrConfig.CANONICAL_WIDTH,
        height: Int = OmrConfig.CANONICAL_HEIGHT,
        numDigits: Int = OmrConfig.STUDENT_ID_DIGITS,
        startX: Double = OmrConfig.STUDENT_ID_LEFT_X,
        startY: Double = OmrConfig.STUDENT_ID_TOP_Y,
        colSpacing: Double = OmrConfig.STUDENT_ID_COL_SPACING,
        rowSpacing: Double = OmrConfig.STUDENT_ID_ROW_SPACING
    ): String {
        val idBuilder = StringBuilder()

        for (digitCol in 0 until numDigits) {
            val colX = startX + (digitCol * colSpacing)
            var bestDigit = -1
            var bestRatio = -1.0

            for (rowDigit in 0..9) {
                val rowY = startY + (rowDigit * rowSpacing)
                val metric = analyzeBubble(canonicalGray, width, height, Point2D(colX, rowY), radius = 11.0)
                if (metric.fillRatio > bestRatio) {
                    bestRatio = metric.fillRatio
                    bestDigit = rowDigit
                }
            }

            if (bestRatio >= OmrConfig.FILL_THRESHOLD && bestDigit >= 0) {
                idBuilder.append(bestDigit)
            } else {
                idBuilder.append("0") // Default fallback
            }
        }

        return idBuilder.toString()
    }
}

