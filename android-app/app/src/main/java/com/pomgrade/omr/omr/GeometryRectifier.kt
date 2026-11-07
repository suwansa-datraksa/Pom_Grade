package com.pomgrade.omr.omr

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.max
import kotlin.math.min

/**
 * Stage 1: Global 4-Corner Homography & Perspective Rectification Engine.
 * Warps a camera frame to canonical dimensions (W x H) given 4 corner fiducial coordinates.
 *
 * Credit: จัดทำโดย ครูสุวรรณษา เดชรักษา
 */
class GeometryRectifier {

    data class QuadCorners(
        val topLeft: Point2D,
        val topRight: Point2D,
        val bottomRight: Point2D,
        val bottomLeft: Point2D
    )

    /**
     * Computes the 3x3 Projective Homography Matrix mapping Source Quad to Destination Quad.
     * Uses direct linear transformation (DLT) with Gaussian elimination.
     */
    fun computeHomographyMatrix(src: QuadCorners, dstWidth: Int, dstHeight: Int): DoubleArray {
        // Source points: (x0, y0) .. (x3, y3)
        // Destination points: (0, 0), (W, 0), (W, H), (0, H)
        val srcPts = arrayOf(
            src.topLeft,
            src.topRight,
            src.bottomRight,
            src.bottomLeft
        )
        val dstPts = arrayOf(
            Point2D(0.0, 0.0),
            Point2D(dstWidth.toDouble(), 0.0),
            Point2D(dstWidth.toDouble(), dstHeight.toDouble()),
            Point2D(0.0, dstHeight.toDouble())
        )

        // Set up 8x8 linear system: A * h = b
        // For each point pair (x, y) -> (u, v):
        // [x, y, 1, 0, 0, 0, -u*x, -u*y] [h00..h21]^T = u
        // [0, 0, 0, x, y, 1, -v*x, -v*y] [h00..h21]^T = v
        val a = Array(8) { DoubleArray(8) }
        val b = DoubleArray(8)

        for (i in 0 until 4) {
            val sx = srcPts[i].x
            val sy = srcPts[i].y
            val dx = dstPts[i].x
            val dy = dstPts[i].y

            // Row 2*i
            a[2 * i][0] = sx
            a[2 * i][1] = sy
            a[2 * i][2] = 1.0
            a[2 * i][3] = 0.0
            a[2 * i][4] = 0.0
            a[2 * i][5] = 0.0
            a[2 * i][6] = -dx * sx
            a[2 * i][7] = -dx * sy
            b[2 * i] = dx

            // Row 2*i + 1
            a[2 * i + 1][0] = 0.0
            a[2 * i + 1][1] = 0.0
            a[2 * i + 1][2] = 0.0
            a[2 * i + 1][3] = sx
            a[2 * i + 1][4] = sy
            a[2 * i + 1][5] = 1.0
            a[2 * i + 1][6] = -dy * sx
            a[2 * i + 1][7] = -dy * sy
            b[2 * i + 1] = dy
        }

        // Solve via Gaussian Elimination
        val h = solveLinearSystem(a, b)
        return doubleArrayOf(
            h[0], h[1], h[2],
            h[3], h[4], h[5],
            h[6], h[7], 1.0
        )
    }

    /**
     * Inverts a 3x3 matrix.
     */
    fun invert3x3(m: DoubleArray): DoubleArray {
        val det = m[0] * (m[4] * m[8] - m[5] * m[7]) -
                m[1] * (m[3] * m[8] - m[5] * m[6]) +
                m[2] * (m[3] * m[7] - m[4] * m[6])

        if (kotlin.math.abs(det) < 1e-12) {
            throw IllegalArgumentException("Singular matrix cannot be inverted")
        }

        val invDet = 1.0 / det
        return doubleArrayOf(
            (m[4] * m[8] - m[5] * m[7]) * invDet,
            (m[2] * m[7] - m[1] * m[8]) * invDet,
            (m[1] * m[5] - m[2] * m[4]) * invDet,

            (m[5] * m[6] - m[3] * m[8]) * invDet,
            (m[0] * m[8] - m[2] * m[6]) * invDet,
            (m[2] * m[3] - m[0] * m[5]) * invDet,

            (m[3] * m[7] - m[4] * m[6]) * invDet,
            (m[1] * m[6] - m[0] * m[7]) * invDet,
            (m[0] * m[4] - m[1] * m[3]) * invDet
        )
    }

    /**
     * Warps source bitmap into canonical rectified grayscale byte array.
     */
    fun warpToCanonical(
        srcBitmap: Bitmap,
        corners: QuadCorners,
        dstWidth: Int = OmrConfig.CANONICAL_WIDTH,
        dstHeight: Int = OmrConfig.CANONICAL_HEIGHT
    ): IntArray {
        val hForward = computeHomographyMatrix(corners, dstWidth, dstHeight)
        val hInv = invert3x3(hForward) // Maps (u, v) in canonical plane -> (x, y) in camera source

        val srcW = srcBitmap.width
        val srcH = srcBitmap.height
        val srcPixels = IntArray(srcW * srcH)
        srcBitmap.getPixels(srcPixels, 0, srcW, 0, 0, srcW, srcH)

        val canonicalPixels = IntArray(dstWidth * dstHeight)

        for (v in 0 until dstHeight) {
            val vOffset = v * dstWidth
            for (u in 0 until dstWidth) {
                // Apply inverse homography: [x', y', w']^T = H_inv * [u, v, 1]^T
                val wPrime = hInv[6] * u + hInv[7] * v + hInv[8]
                val xPrime = (hInv[0] * u + hInv[1] * v + hInv[2]) / wPrime
                val yPrime = (hInv[3] * u + hInv[4] * v + hInv[5]) / wPrime

                val ix = xPrime.toInt()
                val iy = yPrime.toInt()

                if (ix in 0 until (srcW - 1) && iy in 0 until (srcH - 1)) {
                    // Bilinear interpolation
                    val fx = xPrime - ix
                    val fy = yPrime - iy

                    val p00 = srcPixels[iy * srcW + ix]
                    val p10 = srcPixels[iy * srcW + (ix + 1)]
                    val p01 = srcPixels[(iy + 1) * srcW + ix]
                    val p11 = srcPixels[(iy + 1) * srcW + (ix + 1)]

                    val g00 = (Color.red(p00) * 0.299 + Color.green(p00) * 0.587 + Color.blue(p00) * 0.114).toInt()
                    val g10 = (Color.red(p10) * 0.299 + Color.green(p10) * 0.587 + Color.blue(p10) * 0.114).toInt()
                    val g01 = (Color.red(p01) * 0.299 + Color.green(p01) * 0.587 + Color.blue(p01) * 0.114).toInt()
                    val g11 = (Color.red(p11) * 0.299 + Color.green(p11) * 0.587 + Color.blue(p11) * 0.114).toInt()

                    val top = g00 * (1.0 - fx) + g10 * fx
                    val bottom = g01 * (1.0 - fx) + g11 * fx
                    val gray = (top * (1.0 - fy) + bottom * fy).toInt().coerceIn(0, 255)

                    canonicalPixels[vOffset + u] = gray
                } else {
                    canonicalPixels[vOffset + u] = 255 // White background outside
                }
            }
        }

        return canonicalPixels
    }

    private fun solveLinearSystem(a: Array<DoubleArray>, b: DoubleArray): DoubleArray {
        val n = b.size
        for (p in 0 until n) {
            // Find pivot
            var max = p
            for (i in p + 1 until n) {
                if (kotlin.math.abs(a[i][p]) > kotlin.math.abs(a[max][p])) {
                    max = i
                }
            }
            val tempA = a[p]; a[p] = a[max]; a[max] = tempA
            val tempB = b[p]; b[p] = b[max]; b[max] = tempB

            if (kotlin.math.abs(a[p][p]) < 1e-12) {
                continue
            }

            for (i in p + 1 until n) {
                val alpha = a[i][p] / a[p][p]
                b[i] -= alpha * b[p]
                for (j in p until n) {
                    a[i][j] -= alpha * a[p][j]
                }
            }
        }

        // Back substitution
        val x = DoubleArray(n)
        for (i in n - 1 downTo 0) {
            var sum = 0.0
            for (j in i + 1 until n) {
                sum += a[i][j] * x[j]
            }
            x[i] = if (kotlin.math.abs(a[i][i]) > 1e-12) (b[i] - sum) / a[i][i] else 0.0
        }
        return x
    }
}

