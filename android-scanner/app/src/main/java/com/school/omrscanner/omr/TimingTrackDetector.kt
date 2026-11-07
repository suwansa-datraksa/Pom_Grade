package com.school.omrscanner.omr

import com.school.omrscanner.template.TemplateModel
import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.core.Rect
import org.opencv.imgproc.Imgproc
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Stage 2: Dual-Sided Timing Track Extraction & Residual Validation.
 *
 * Responsibilities:
 *  1. Extracts Left and Right timing track strips from canonical 1000x1414 Mat.
 *  2. Evaluates horizontal timing bar centroids (P_L,i and P_R,i) per question row.
 *  3. Performs Residual Validation:
 *     - Missing row interpolation via linear spline.
 *     - Rejects scan if >10% rows missing.
 *     - Flags cylindrical sag / excessive paper curl based on standard deviation of delta Y.
 *
 * Credit: จัดทำโดย ครูสุวรรณษา เดชรักษา
 */
class TimingTrackDetector {

    data class RowTimingTrack(
        val rowIndex: Int,
        val pLeft: Point,
        val pRight: Point,
        val isInterpolated: Boolean = false
    )

    data class TrackExtractionResult(
        val tracks: List<RowTimingTrack>,
        val missingCount: Int,
        val deltaYStdDev: Double,
        val isCurvedCylindrical: Boolean,
        val isValid: Boolean,
        val errorMessage: String? = null
    )

    /**
     * Extracts row timing track pairs from canonical Mat.
     *
     * @param canonicalMat Warped canonical 1000x1414 px image (Grayscale or RGBA)
     * @param template Template model containing expected timing track positions in mm
     * @return TrackExtractionResult
     */
    fun extractTimingTracks(
        canonicalMat: Mat,
        template: TemplateModel
    ): TrackExtractionResult {
        val gray = Mat()
        if (canonicalMat.channels() > 1) {
            Imgproc.cvtColor(canonicalMat, gray, Imgproc.COLOR_RGBA2GRAY)
        } else {
            canonicalMat.copyTo(gray)
        }

        val widthPx = canonicalMat.width().toDouble()
        val heightPx = canonicalMat.height().toDouble()

        // mm to pixel scale factors for 1000x1414 canonical plane
        val scaleX = widthPx / template.pageSize.widthMm
        val scaleY = heightPx / template.pageSize.heightMm

        val numRows = template.timingTracks.leftBars.size
        val detectedLeft = arrayOfNulls<Point>(numRows)
        val detectedRight = arrayOfNulls<Point>(numRows)

        // 1. Process each row's Left and Right timing bars in local search windows
        val windowRadiusX = (scaleX * 6.0).toInt() // +/- 6 mm horizontal window
        val windowRadiusY = (scaleY * 2.8).toInt() // +/- 2.8 mm vertical window

        for (r in 0 until numRows) {
            val leftBarTemplate = template.timingTracks.leftBars[r]
            val rightBarTemplate = template.timingTracks.rightBars[r]

            val expectedLeftX = (leftBarTemplate.centerXmm ?: (leftBarTemplate.xMm + leftBarTemplate.widthMm / 2)) * scaleX
            val expectedLeftY = (leftBarTemplate.centerYmm ?: (leftBarTemplate.yMm + leftBarTemplate.heightMm / 2)) * scaleY

            val expectedRightX = (rightBarTemplate.centerXmm ?: (rightBarTemplate.xMm + rightBarTemplate.widthMm / 2)) * scaleX
            val expectedRightY = (rightBarTemplate.centerYmm ?: (rightBarTemplate.yMm + rightBarTemplate.heightMm / 2)) * scaleY

            detectedLeft[r] = findDarkCentroid(gray, expectedLeftX.toInt(), expectedLeftY.toInt(), windowRadiusX, windowRadiusY)
            detectedRight[r] = findDarkCentroid(gray, expectedRightX.toInt(), expectedRightY.toInt(), windowRadiusX, windowRadiusY)
        }

        gray.release()

        // 2. Count missing bars and apply linear spline interpolation for missing rows
        var missingRowsCount = 0
        val resolvedTracks = ArrayList<RowTimingTrack>(numRows)

        for (r in 0 until numRows) {
            var pL = detectedLeft[r]
            var pR = detectedRight[r]
            var isInterpolated = false

            if (pL == null || pR == null) {
                missingRowsCount++
                isInterpolated = true

                // Interpolate from nearest available neighbors
                pL = pL ?: interpolatePoint(detectedLeft, r, numRows, scaleX, scaleY, template, isLeft = true)
                pR = pR ?: interpolatePoint(detectedRight, r, numRows, scaleX, scaleY, template, isLeft = false)
            }

            resolvedTracks.add(RowTimingTrack(r, pL, pR, isInterpolated))
        }

        // 3. Validation Rules:
        // Rule A: Abort if missing rows > 10%
        val maxAllowedMissing = (numRows * 0.10).toInt().coerceAtLeast(1)
        if (missingRowsCount > maxAllowedMissing) {
            return TrackExtractionResult(
                tracks = resolvedTracks,
                missingCount = missingRowsCount,
                deltaYStdDev = 0.0,
                isCurvedCylindrical = false,
                isValid = false,
                errorMessage = "ตรวจไม่พบแถบ Timing Track เกิน 10% ($missingRowsCount/$numRows แถว) กรุณาถ่ายภาพใหม่"
            )
        }

        // Rule B: Evaluate vertical spacing Standard Deviation to detect excessive page curl
        val deltaYList = mutableListOf<Double>()
        for (r in 1 until numRows) {
            val dy = (resolvedTracks[r].pLeft.y + resolvedTracks[r].pRight.y) / 2.0 -
                     (resolvedTracks[r - 1].pLeft.y + resolvedTracks[r - 1].pRight.y) / 2.0
            deltaYList.add(dy)
        }

        val avgDeltaY = deltaYList.average()
        val variance = deltaYList.map { (it - avgDeltaY) * (it - avgDeltaY) }.average()
        val stdDev = sqrt(variance)

        // Threshold for excessive non-linear warping
        val isCurved = stdDev > (avgDeltaY * 0.22)

        return TrackExtractionResult(
            tracks = resolvedTracks,
            missingCount = missingRowsCount,
            deltaYStdDev = stdDev,
            isCurvedCylindrical = isCurved,
            isValid = true,
            errorMessage = if (isCurved) "ตรวจพบความโค้งของกระดาษ (ชดเชยด้วยสมการเวกเตอร์ P_i(t) สำเร็จ)" else null
        )
    }

    /**
     * Local intensity-weighted centroid finder for timing mark.
     */
    private fun findDarkCentroid(
        gray: Mat,
        centerX: Int,
        centerY: Int,
        radiusX: Int,
        radiusY: Int
    ): Point? {
        val minX = (centerX - radiusX).coerceIn(0, gray.width() - 1)
        val maxX = (centerX + radiusX).coerceIn(0, gray.width() - 1)
        val minY = (centerY - radiusY).coerceIn(0, gray.height() - 1)
        val maxY = (centerY + radiusY).coerceIn(0, gray.height() - 1)

        val roiWidth = maxX - minX + 1
        val roiHeight = maxY - minY + 1
        if (roiWidth <= 0 || roiHeight <= 0) return null

        val subMat = gray.submat(Rect(minX, minY, roiWidth, roiHeight))
        val buffer = ByteArray(roiWidth * roiHeight)
        subMat.get(0, 0, buffer)
        subMat.release()

        var weightedX = 0.0
        var weightedY = 0.0
        var totalWeight = 0.0

        for (y in 0 until roiHeight) {
            val rowOffset = y * roiWidth
            for (x in 0 until roiWidth) {
                val luma = buffer[rowOffset + x].toInt() and 0xFF
                // Dark pixels have low luma; invert to give high weight
                val darkness = 255 - luma
                if (darkness > 128) {
                    weightedX += (minX + x) * darkness
                    weightedY += (minY + y) * darkness
                    totalWeight += darkness
                }
            }
        }

        // Must have sufficient dark pixels to qualify as timing block
        if (totalWeight < 1000.0) {
            return null
        }

        return Point(weightedX / totalWeight, weightedY / totalWeight)
    }

    private fun interpolatePoint(
        detected: Array<Point?>,
        targetRow: Int,
        totalRows: Int,
        scaleX: Double,
        scaleY: Double,
        template: TemplateModel,
        isLeft: Boolean
    ): Point {
        // Find nearest valid neighbors
        var prevIdx = -1
        for (i in (targetRow - 1) downTo 0) {
            if (detected[i] != null) { prevIdx = i; break }
        }

        var nextIdx = -1
        for (i in (targetRow + 1) until totalRows) {
            if (detected[i] != null) { nextIdx = i; break }
        }

        if (prevIdx != -1 && nextIdx != -1) {
            val pPrev = detected[prevIdx]!!
            val pNext = detected[nextIdx]!!
            val factor = (targetRow - prevIdx).toDouble() / (nextIdx - prevIdx).toDouble()
            val ix = pPrev.x + factor * (pNext.x - pPrev.x)
            val iy = pPrev.y + factor * (pNext.y - pPrev.y)
            return Point(ix, iy)
        }

        if (prevIdx != -1) {
            val pPrev = detected[prevIdx]!!
            val barPrev = if (isLeft) template.timingTracks.leftBars[prevIdx] else template.timingTracks.rightBars[prevIdx]
            val barCurr = if (isLeft) template.timingTracks.leftBars[targetRow] else template.timingTracks.rightBars[targetRow]
            val dy = (barCurr.yMm - barPrev.yMm) * scaleY
            return Point(pPrev.x, pPrev.y + dy)
        }

        if (nextIdx != -1) {
            val pNext = detected[nextIdx]!!
            val barNext = if (isLeft) template.timingTracks.leftBars[nextIdx] else template.timingTracks.rightBars[nextIdx]
            val barCurr = if (isLeft) template.timingTracks.leftBars[targetRow] else template.timingTracks.rightBars[targetRow]
            val dy = (barCurr.yMm - barNext.yMm) * scaleY
            return Point(pNext.x, pNext.y + dy)
        }

        // Fallback to template position
        val bar = if (isLeft) template.timingTracks.leftBars[targetRow] else template.timingTracks.rightBars[targetRow]
        return Point((bar.xMm + bar.widthMm / 2) * scaleX, (bar.yMm + bar.heightMm / 2) * scaleY)
    }
}

