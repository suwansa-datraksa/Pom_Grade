package com.pomgrade.omr.omr

/**
 * Stage 2: Dual-Sided Timing Track Interpolator.
 * Compensates for paper curls, wrinkles, and non-linear local distortions using:
 * P_i(t) = (1 - t)*P_L,i + t*P_R,i
 *
 * Credit: จัดทำโดย ครูสุวรรณษา เดชรักษา
 */
class TimingTrackInterpolator {

    data class RowTimingPair(
        val rowIndex: Int,
        val pLeft: Point2D,
        val pRight: Point2D
    )

    /**
     * Extracts and refines the dual-sided timing tracks for all question rows.
     * Searches locally in the canonical rectified grayscale plane to locate the precise
     * dark centroid of the timing blocks on both the left and right margins.
     */
    fun extractRowTimingTracks(
        canonicalGray: IntArray,
        width: Int = OmrConfig.CANONICAL_WIDTH,
        height: Int = OmrConfig.CANONICAL_HEIGHT,
        numRows: Int = OmrConfig.NUM_ROWS,
        startY: Double = OmrConfig.QUESTIONS_COL1_START_Y,
        leftX: Double = OmrConfig.QUESTIONS_COL1_LEFT_X,
        rightX: Double = OmrConfig.QUESTIONS_COL1_RIGHT_X,
        rowHeight: Double = OmrConfig.ROW_HEIGHT_PX
    ): List<RowTimingPair> {
        val pairs = ArrayList<RowTimingPair>(numRows)

        for (i in 0 until numRows) {
            val expectedY = startY + (i * rowHeight)

            // Refine Left Timing Block centroid in local window
            val pL = refineTimingBlockCentroid(
                canonicalGray, width, height,
                centerX = leftX.toInt(),
                centerY = expectedY.toInt(),
                windowRadius = 14
            )

            // Refine Right Timing Block centroid in local window
            val pR = refineTimingBlockCentroid(
                canonicalGray, width, height,
                centerX = rightX.toInt(),
                centerY = expectedY.toInt(),
                windowRadius = 14
            )

            pairs.add(RowTimingPair(i, pL, pR))
        }

        return pairs
    }

    /**
     * Evaluates the precise coordinates of question choice bubble:
     * P_i(t) = (1 - t)*P_L,i + t*P_R,i
     *
     * @param timingPair Left and Right timing mark centroids for row i
     * @param t Normalized horizontal interpolation factor in [0, 1]
     */
    fun evaluateBubbleCoordinate(timingPair: RowTimingPair, t: Double): Point2D {
        return Point2D.interpolate(timingPair.pLeft, timingPair.pRight, t)
    }

    /**
     * Refines timing block center using intensity-weighted centroid in local window.
     */
    private fun refineTimingBlockCentroid(
        gray: IntArray,
        width: Int,
        height: Int,
        centerX: Int,
        centerY: Int,
        windowRadius: Int
    ): Point2D {
        var weightedX = 0.0
        var weightedY = 0.0
        var totalWeight = 0.0

        val minX = (centerX - windowRadius).coerceIn(0, width - 1)
        val maxX = (centerX + windowRadius).coerceIn(0, width - 1)
        val minY = (centerY - windowRadius).coerceIn(0, height - 1)
        val maxY = (centerY + windowRadius).coerceIn(0, height - 1)

        for (y in minY..maxY) {
            val offset = y * width
            for (x in minX..maxX) {
                val luma = gray[offset + x]
                // Timing marks are dark (low luma). Invert luma to give higher weight to dark pixels.
                val darkness = (255 - luma).coerceAtLeast(0)
                if (darkness > 120) {
                    weightedX += x * darkness
                    weightedY += y * darkness
                    totalWeight += darkness
                }
            }
        }

        return if (totalWeight > 0.0) {
            Point2D(weightedX / totalWeight, weightedY / totalWeight)
        } else {
            Point2D(centerX, centerY)
        }
    }
}

