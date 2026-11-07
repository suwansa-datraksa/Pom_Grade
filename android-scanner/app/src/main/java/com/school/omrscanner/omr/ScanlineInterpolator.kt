package com.school.omrscanner.omr

import org.opencv.core.Point

/**
 * Stage 2 Scanline Interpolator.
 * Compensates for paper curvature, cylindrical sagging, slant, and page curl using:
 *
 * P_i(t) = (1 - t) * P_L,i + t * P_R,i
 *
 * where:
 *  - P_L,i = (x_L,i, y_L,i) is the left timing bar centroid for row i
 *  - P_R,i = (x_R,i, y_R,i) is the right timing bar centroid for row i
 *  - t in [0, 1] is the normalized horizontal position of each choice bubble
 *
 * Credit: จัดทำโดย ครูสุวรรณษา เดชรักษา
 */
class ScanlineInterpolator {

    /**
     * Evaluates bubble sampling center using row-specific local interpolation.
     *
     * @param track Left and Right timing track coordinates for row i
     * @param t Normalized horizontal factor in [0.0, 1.0]
     * @return Precise 2D subpixel coordinate Point(x, y)
     */
    fun interpolate(track: TimingTrackDetector.RowTimingTrack, t: Double): Point {
        require(t in 0.0..1.0) { "Parameter t must be in normalized interval [0.0, 1.0], got $t" }

        val x = (1.0 - t) * track.pLeft.x + t * track.pRight.x
        val y = (1.0 - t) * track.pLeft.y + t * track.pRight.y

        return Point(x, y)
    }

    /**
     * Non-interpolated rigid grid evaluator (used as baseline control in benchmark tests).
     * Assumes flat rigid rectangle without row-by-row curvature correction.
     */
    fun evaluateRigidGrid(
        firstTrack: TimingTrackDetector.RowTimingTrack,
        lastTrack: TimingTrackDetector.RowTimingTrack,
        rowIndex: Int,
        totalRows: Int,
        t: Double
    ): Point {
        val rowRatio = rowIndex.toDouble() / (totalRows - 1).coerceAtLeast(1).toDouble()

        // Rigid bounding box corners
        val rigidTopLeft = firstTrack.pLeft
        val rigidTopRight = firstTrack.pRight
        val rigidBottomLeft = lastTrack.pLeft
        val rigidBottomRight = lastTrack.pRight

        val topX = (1.0 - t) * rigidTopLeft.x + t * rigidTopRight.x
        val topY = (1.0 - t) * rigidTopLeft.y + t * rigidTopRight.y

        val bottomX = (1.0 - t) * rigidBottomLeft.x + t * rigidBottomRight.x
        val bottomY = (1.0 - t) * rigidBottomLeft.y + t * rigidBottomRight.y

        val x = (1.0 - rowRatio) * topX + rowRatio * bottomX
        val y = (1.0 - rowRatio) * topY + rowRatio * bottomY

        return Point(x, y)
    }

    /**
     * Computes the curvature deviation (residual displacement in pixels)
     * between row-interpolated coordinates and a rigid non-interpolated grid.
     */
    fun computeCurvatureDeviation(
        track: TimingTrackDetector.RowTimingTrack,
        firstTrack: TimingTrackDetector.RowTimingTrack,
        lastTrack: TimingTrackDetector.RowTimingTrack,
        rowIndex: Int,
        totalRows: Int,
        t: Double = 0.50 // Center of page exhibits maximum sagging
    ): Double {
        val interpolatedPt = interpolate(track, t)
        val rigidPt = evaluateRigidGrid(firstTrack, lastTrack, rowIndex, totalRows, t)

        val dx = interpolatedPt.x - rigidPt.x
        val dy = interpolatedPt.y - rigidPt.y
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }
}
