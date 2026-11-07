package com.pomgrade.omr.ui.components

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.pomgrade.omr.omr.GeometryRectifier
import com.pomgrade.omr.omr.Point2D

/**
 * Custom View Overlay for Camera Viewfinder.
 * Renders corner guides, fiducial target indicators, and dynamic quadrilateral bounding boxes.
 *
 * Credit: จัดทำโดย ครูสุวรรณษา เดชรักษา
 */
class ScannerOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val scrimPaint = Paint().apply {
        color = Color.parseColor("#77000000")
        style = Paint.Style.FILL
    }

    private val targetGuidePaint = Paint().apply {
        color = Color.parseColor("#38BDF8")
        style = Paint.Style.STROKE
        strokeWidth = 4f
        isAntiAlias = true
    }

    private val detectedQuadPaint = Paint().apply {
        color = Color.parseColor("#22C55E")
        style = Paint.Style.STROKE
        strokeWidth = 6f
        isAntiAlias = true
    }

    private val cornerPillPaint = Paint().apply {
        color = Color.parseColor("#FACC15")
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private var detectedCorners: GeometryRectifier.QuadCorners? = null
    private var isTargetLocked = false

    fun updateDetectedCorners(corners: GeometryRectifier.QuadCorners?) {
        this.detectedCorners = corners
        this.isTargetLocked = corners != null
        postInvalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()

        // 1. Draw Default Alignment Guide Frame (A4 aspect ~ 1 : 1.414)
        val guideW = w * 0.85f
        val guideH = guideW * 1.35f
        val left = (w - guideW) / 2f
        val top = (h - guideH) / 2f
        val right = left + guideW
        val bottom = top + guideH

        val guideRect = RectF(left, top, right, bottom)

        // Draw 4 corner brackets
        val bracketLen = 50f
        val path = Path().apply {
            // Top-Left
            moveTo(left, top + bracketLen)
            lineTo(left, top)
            lineTo(left + bracketLen, top)

            // Top-Right
            moveTo(right - bracketLen, top)
            lineTo(right, top)
            lineTo(right, top + bracketLen)

            // Bottom-Right
            moveTo(right, bottom - bracketLen)
            lineTo(right, bottom)
            lineTo(right - bracketLen, bottom)

            // Bottom-Left
            moveTo(left + bracketLen, bottom)
            lineTo(left, bottom)
            lineTo(left, bottom - bracketLen)
        }

        canvas.drawPath(path, if (isTargetLocked) detectedQuadPaint else targetGuidePaint)

        // 2. Draw Detected Quadrilateral & Fiducials if available
        detectedCorners?.let { c ->
            val quadPath = Path().apply {
                moveTo(c.topLeft.x.toFloat(), c.topLeft.y.toFloat())
                lineTo(c.topRight.x.toFloat(), c.topRight.y.toFloat())
                lineTo(c.bottomRight.x.toFloat(), c.bottomRight.y.toFloat())
                lineTo(c.bottomLeft.x.toFloat(), c.bottomLeft.y.toFloat())
                close()
            }
            canvas.drawPath(quadPath, detectedQuadPaint)

            // Corner indicator dots
            drawCornerDot(canvas, c.topLeft)
            drawCornerDot(canvas, c.topRight)
            drawCornerDot(canvas, c.bottomRight)
            drawCornerDot(canvas, c.bottomLeft)
        }
    }

    private fun drawCornerDot(canvas: Canvas, pt: Point2D) {
        canvas.drawCircle(pt.x.toFloat(), pt.y.toFloat(), 12f, cornerPillPaint)
    }
}

