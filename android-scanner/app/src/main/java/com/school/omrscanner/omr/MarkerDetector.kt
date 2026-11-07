package com.school.omrscanner.omr

import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Stage 1: OpenCV-based Marker Detection & Geometric Rectification Engine.
 *
 * Responsibilities:
 *  1. Adaptive thresholding and contour hierarchy filtering to detect 4 corner fiducials.
 *  2. Distinguish the 14x14 mm Top-Left fiducial from the 8x8 mm fiducials.
 *  3. Auto-orient / rotate frame if rotated 90, 180, or 270 degrees.
 *  4. Warp perspective to standard 1000x1414 px canonical plane.
 *
 * Credit: จัดทำโดย ครูสุวรรณษา เดชรักษา
 */
class MarkerDetector {

    companion object {
        const val CANONICAL_WIDTH = 1000.0
        const val CANONICAL_HEIGHT = 1414.0
        private const val AREA_RATIO_THRESHOLD = 2.0 // (14/8)^2 = 3.0625
    }

    data class DetectionResult(
        val warpedCanonical: Mat,
        val rotationAngle: Int, // 0, 90, 180, 270
        val detectedCorners: List<Point>, // TL, TR, BR, BL in source image
        val confidence: Double
    )

    data class DetectedMarker(
        val center: Point,
        val area: Double,
        val contour: MatOfPoint2f
    )

    /**
     * Executes the complete fiducial detection and perspective rectification pipeline.
     *
     * @param inputMat Input camera frame (RGBA or BGR or Grayscale)
     * @return DetectionResult containing warped 1000x1414 Mat, or null if 4 markers not found
     */
    fun processFrame(inputMat: Mat): DetectionResult? {
        val gray = Mat()
        if (inputMat.channels() > 1) {
            Imgproc.cvtColor(inputMat, gray, Imgproc.COLOR_RGBA2GRAY)
        } else {
            inputMat.copyTo(gray)
        }

        // 1. Preprocessing: Gaussian Blur + Adaptive Thresholding
        val blurred = Mat()
        Imgproc.GaussianBlur(gray, blurred, Size(5.0, 5.0), 0.0)

        val thresh = Mat()
        Imgproc.adaptiveThreshold(
            blurred,
            thresh,
            255.0,
            Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
            Imgproc.THRESH_BINARY_INV,
            19,
            5.0
        )

        // 2. Contour Hierarchy Filtering
        val contours = mutableListOf<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(thresh, contours, hierarchy, Imgproc.RETR_TREE, Imgproc.CHAIN_APPROX_SIMPLE)

        val candidateMarkers = mutableListOf<DetectedMarker>()
        val totalFrameArea = inputMat.width() * inputMat.height()

        for (cnt in contours) {
            val cnt2f = MatOfPoint2f(*cnt.toArray())
            val peri = Imgproc.arcLength(cnt2f, true)
            val approx = MatOfPoint2f()
            Imgproc.approxPolyDP(cnt2f, approx, 0.04 * peri, true)

            // Fiducial markers must be quadrilaterals (4 vertices)
            if (approx.total() == 4L) {
                val area = Imgproc.contourArea(approx)
                // Filter out contours that are too small (<0.01% of frame) or too large (>10% of frame)
                if (area > (totalFrameArea * 0.0001) && area < (totalFrameArea * 0.10)) {
                    val rect = Imgproc.boundingRect(MatOfPoint(*approx.toArray()))
                    val aspectRatio = rect.width.toDouble() / rect.height.toDouble()
                    // Must be square-like (aspect ratio ~ 1.0)
                    if (aspectRatio in 0.70..1.40) {
                        val moments = Imgproc.moments(approx)
                        if (moments._m00 > 0.0) {
                            val cx = moments._m10 / moments._m00
                            val cy = moments._m01 / moments._m00
                            candidateMarkers.add(DetectedMarker(Point(cx, cy), area, approx))
                        }
                    }
                }
            }
        }

        // Clean up temporary Mats
        blurred.release()
        thresh.release()
        hierarchy.release()

        // 3. Cluster and find the 4 outermost corner markers
        if (candidateMarkers.size < 4) {
            gray.release()
            return null
        }

        val fourCorners = selectFourCorners(candidateMarkers, inputMat.width(), inputMat.height())
        if (fourCorners == null) {
            gray.release()
            return null
        }

        // 4. Orientation & Top-Left 14x14 mm Marker Identification
        // The Top-Left marker area is ~3x larger than the 8x8 mm markers
        val sortedByArea = fourCorners.sortedByDescending { it.area }
        val largestMarker = sortedByArea[0]
        val secondLargest = sortedByArea[1]

        // Check area ratio
        val areaRatio = largestMarker.area / secondLargest.area
        val hasDistinctLargeMarker = areaRatio >= AREA_RATIO_THRESHOLD

        // Determine orientation based on quadrant location of the largest marker
        val centerFrameX = inputMat.width() / 2.0
        val centerFrameY = inputMat.height() / 2.0

        val rotationAngle = when {
            largestMarker.center.x <= centerFrameX && largestMarker.center.y <= centerFrameY -> 0   // Top-Left (Normal)
            largestMarker.center.x > centerFrameX && largestMarker.center.y <= centerFrameY -> 90   // Top-Right (Rotated 90 deg)
            largestMarker.center.x > centerFrameX && largestMarker.center.y > centerFrameY -> 180   // Bottom-Right (Rotated 180 deg)
            else -> 270 // Bottom-Left (Rotated 270 deg)
        }

        // 5. Rotate Frame to Canonical Orientation if necessary
        val properlyOrientedMat = Mat()
        when (rotationAngle) {
            0 -> inputMat.copyTo(properlyOrientedMat)
            90 -> Core.rotate(inputMat, properlyOrientedMat, Core.ROTATE_90_COUNTERCLOCKWISE)
            180 -> Core.rotate(inputMat, properlyOrientedMat, Core.ROTATE_180)
            270 -> Core.rotate(inputMat, properlyOrientedMat, Core.ROTATE_90_CLOCKWISE)
        }

        // 6. Map source corner points ordered: TL, TR, BR, BL
        val orderedSourcePts = orderCornerPoints(fourCorners.map { it.center }, rotationAngle, inputMat.width(), inputMat.height())

        // 7. Compute Perspective Transform to standard 1000 x 1414 px
        val srcPtsMat = MatOfPoint2f(
            orderedSourcePts[0],
            orderedSourcePts[1],
            orderedSourcePts[2],
            orderedSourcePts[3]
        )

        val dstPtsMat = MatOfPoint2f(
            Point(0.0, 0.0),
            Point(CANONICAL_WIDTH, 0.0),
            Point(CANONICAL_WIDTH, CANONICAL_HEIGHT),
            Point(0.0, CANONICAL_HEIGHT)
        )

        val transformMatrix = Imgproc.getPerspectiveTransform(srcPtsMat, dstPtsMat)
        val warpedResult = Mat()
        Imgproc.warpPerspective(
            inputMat,
            warpedResult,
            transformMatrix,
            Size(CANONICAL_WIDTH, CANONICAL_HEIGHT),
            Imgproc.INTER_LINEAR
        )

        // Clean up
        gray.release()
        properlyOrientedMat.release()
        srcPtsMat.release()
        dstPtsMat.release()
        transformMatrix.release()

        val confidence = if (hasDistinctLargeMarker) 0.98 else 0.85

        return DetectionResult(
            warpedCanonical = warpedResult,
            rotationAngle = rotationAngle,
            detectedCorners = orderedSourcePts,
            confidence = confidence
        )
    }

    /**
     * Clusters candidate markers into 4 quadrants to pick the most extreme outer corners.
     */
    private fun selectFourCorners(
        candidates: List<DetectedMarker>,
        width: Int,
        height: Int
    ): List<DetectedMarker>? {
        val midX = width / 2.0
        val midY = height / 2.0

        val tlList = candidates.filter { it.center.x <= midX && it.center.y <= midY }
        val trList = candidates.filter { it.center.x > midX && it.center.y <= midY }
        val brList = candidates.filter { it.center.x > midX && it.center.y > midY }
        val blList = candidates.filter { it.center.x <= midX && it.center.y > midY }

        if (tlList.isEmpty() || trList.isEmpty() || brList.isEmpty() || blList.isEmpty()) {
            return null
        }

        // In each quadrant, choose the point closest to the outer corner
        val tl = tlList.minByOrNull { distanceSq(it.center, Point(0.0, 0.0)) }!!
        val tr = trList.minByOrNull { distanceSq(it.center, Point(width.toDouble(), 0.0)) }!!
        val br = brList.minByOrNull { distanceSq(it.center, Point(width.toDouble(), height.toDouble())) }!!
        val bl = blList.minByOrNull { distanceSq(it.center, Point(0.0, height.toDouble())) }!!

        return listOf(tl, tr, br, bl)
    }

    /**
     * Sorts 4 points into canonical clockwise order starting at Top-Left:
     * [0] = Top-Left
     * [1] = Top-Right
     * [2] = Bottom-Right
     * [3] = Bottom-Left
     */
    private fun orderCornerPoints(
        pts: List<Point>,
        rotationAngle: Int,
        width: Int,
        height: Int
    ): List<Point> {
        // Center of mass
        val cx = pts.map { it.x }.average()
        val cy = pts.map { it.y }.average()

        val tl = pts.first { it.x <= cx && it.y <= cy }
        val tr = pts.first { it.x > cx && it.y <= cy }
        val br = pts.first { it.x > cx && it.y > cy }
        val bl = pts.first { it.x <= cx && it.y > cy }

        return when (rotationAngle) {
            0 -> listOf(tl, tr, br, bl)
            90 -> listOf(bl, tl, tr, br)
            180 -> listOf(br, bl, tl, tr)
            270 -> listOf(tr, br, bl, tl)
            else -> listOf(tl, tr, br, bl)
        }
    }

    private fun distanceSq(p1: Point, p2: Point): Double {
        val dx = p1.x - p2.x
        val dy = p1.y - p2.y
        return dx * dx + dy * dy
    }
}

