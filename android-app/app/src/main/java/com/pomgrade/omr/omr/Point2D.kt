package com.pomgrade.omr.omr

import kotlin.math.sqrt

/**
 * High-precision 2D Point and Vector representation.
 * Supports vector mathematics for two-stage geometric interpolation:
 * P_i(t) = (1 - t)*P_L,i + t*P_R,i
 *
 * Credit: จัดทำโดย ครูสุวรรณษา เดชรักษา
 */
data class Point2D(val x: Double, val y: Double) {
    constructor(x: Float, y: Float) : this(x.toDouble(), y.toDouble())
    constructor(x: Int, y: Int) : this(x.toDouble(), y.toDouble())

    operator fun plus(other: Point2D): Point2D = Point2D(x + other.x, y + other.y)
    operator fun minus(other: Point2D): Point2D = Point2D(x - other.x, y - other.y)
    operator fun times(scalar: Double): Point2D = Point2D(x * scalar, y * scalar)

    fun distanceTo(other: Point2D): Double {
        val dx = x - other.x
        val dy = y - other.y
        return sqrt(dx * dx + dy * dy)
    }

    companion object {
        val ZERO = Point2D(0.0, 0.0)

        /**
         * Row-by-row interpolation using dual-sided timing tracks:
         * P_i(t) = (1 - t)*P_L,i + t*P_R,i
         */
        fun interpolate(pL: Point2D, pR: Point2D, t: Double): Point2D {
            return (pL * (1.0 - t)) + (pR * t)
        }
    }
}
