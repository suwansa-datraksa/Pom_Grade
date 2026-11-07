package com.pomgrade.omr.omr

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.abs

/**
 * Detects the 4 corner fiducial markers on the OMR sheet.
 * Quadrants:
 *  1. Top-Left (TL)
 *  2. Top-Right (TR)
 *  3. Bottom-Right (BR)
 *  4. Bottom-Left (BL)
 *
 * Credit: จัดทำโดย ครูสุวรรณษา เดชรักษา
 */
class FiducialDetector {

    data class DetectionResult(
        val corners: GeometryRectifier.QuadCorners,
        val confidence: Double,
        val skewAngleDegrees: Double
    )

    /**
     * Finds 4 outer fiducials in the camera frame bitmap.
     * Uses quadrant-localized adaptive thresholding and centroid tracking.
     */
    fun detectFiducials(bitmap: Bitmap): DetectionResult? {
        val w = bitmap.width
        val h = bitmap.height

        // Downsample for speed (e.g. process on ~640x480 or scaled grid)
        val scale = if (w > 800) 2 else 1
        val sw = w / scale
        val sh = h / scale

        // Extract grayscale
        val gray = IntArray(sw * sh)
        var sumLuma = 0L

        for (y in 0 until sh) {
            val srcY = y * scale
            for (x in 0 until sw) {
                val srcX = x * scale
                val p = bitmap.getPixel(srcX, srcY)
                val luma = (Color.red(p) * 299 + Color.green(p) * 587 + Color.blue(p) * 114) / 1000
                gray[y * sw + x] = luma
                sumLuma += luma
            }
        }

        // Global adaptive threshold
        val avgLuma = (sumLuma / (sw * sh)).toInt()
        val darkThreshold = (avgLuma * 0.55).toInt().coerceIn(30, 110)

        // Search windows: 25% of width/height from each corner
        val marginW = (sw * 0.28).toInt()
        val marginH = (sh * 0.28).toInt()

        val tl = findFiducialCentroid(gray, sw, sh, 0, marginW, 0, marginH, darkThreshold)
        val tr = findFiducialCentroid(gray, sw, sh, sw - marginW, sw, 0, marginH, darkThreshold)
        val br = findFiducialCentroid(gray, sw, sh, sw - marginW, sw, sh - marginH, sh, darkThreshold)
        val bl = findFiducialCentroid(gray, sw, sh, 0, marginW, sh - marginH, sh, darkThreshold)

        if (tl == null || tr == null || br == null || bl == null) {
            return null
        }

        // Scale coordinates back to original bitmap dimension
        val origTL = Point2D(tl.x * scale, tl.y * scale)
        val origTR = Point2D(tr.x * scale, tr.y * scale)
        val origBR = Point2D(br.x * scale, br.y * scale)
        val origBL = Point2D(bl.x * scale, bl.y * scale)

        // Validate geometric consistency:
        // Top edge vector: TR - TL
        // Bottom edge vector: BR - BL
        // Left edge vector: BL - TL
        // Right edge vector: BR - TR
        val topWidth = origTL.distanceTo(origTR)
        val bottomWidth = origBL.distanceTo(origBR)
        val leftHeight = origTL.distanceTo(origBL)
        val rightHeight = origTR.distanceTo(origBR)

        // Min size check (must occupy at least 40% of camera frame)
        if (topWidth < w * 0.40 || bottomWidth < w * 0.40 ||
            leftHeight < h * 0.40 || rightHeight < h * 0.40) {
            return null
        }

        // Aspect ratio check (A4 should be ~0.65 to 0.85 width/height)
        val avgWidth = (topWidth + bottomWidth) / 2.0
        val avgHeight = (leftHeight + rightHeight) / 2.0
        val aspectRatio = avgWidth / avgHeight
        if (aspectRatio !in 0.55..0.95) {
            return null
        }

        // Calculate skew angle
        val dx = origTR.x - origTL.x
        val dy = origTR.y - origTL.y
        val skewRad = kotlin.math.atan2(dy, dx)
        val skewDeg = Math.toDegrees(skewRad)

        val corners = GeometryRectifier.QuadCorners(
            topLeft = origTL,
            topRight = origTR,
            bottomRight = origBR,
            bottomLeft = origBL
        )

        return DetectionResult(
            corners = corners,
            confidence = 0.95,
            skewAngleDegrees = skewDeg
        )
    }

    private fun findFiducialCentroid(
        gray: IntArray,
        sw: Int,
        sh: Int,
        minX: Int,
        maxX: Int,
        minY: Int,
        maxY: Int,
        darkThreshold: Int
    ): Point2D? {
        var sumX = 0.0
        var sumY = 0.0
        var count = 0

        for (y in minY until maxY) {
            val rowOffset = y * sw
            for (x in minX until maxX) {
                if (gray[rowOffset + x] < darkThreshold) {
                    sumX += x
                    sumY += y
                    count++
                }
            }
        }

        // Minimum pixel area required for fiducial marker
        if (count < 25) {
            return null
        }

        return Point2D(sumX / count, sumY / count)
    }
}
