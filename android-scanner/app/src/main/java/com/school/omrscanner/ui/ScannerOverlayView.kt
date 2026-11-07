package com.school.omrscanner.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

/**
 * Custom overlay HUD view for OMR sheet scanning.
 * Draws fiducial corner target boxes (highlighting the larger 14x14mm Top-Left marker)
 * and scanning status animations.
 *
 * Pom Grade - จัดทำโดย ครูสุวรรณษา เดชรักษา
 */
class ScannerOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class State {
        SEARCHING,
        FIDUCIALS_FOUND,
        PROCESSING,
        SUCCESS
    }

    private var currentState: State = State.SEARCHING

    private val guidePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = Color.parseColor("#3B82F6") // Blue
    }

    private val tlMarkerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
        color = Color.parseColor("#F59E0B") // Amber highlight for 14x14mm orientation fiducial
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#333B82F6")
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 36f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        setShadowLayer(4f, 2f, 2f, Color.BLACK)
    }

    fun setState(state: State) {
        if (currentState != state) {
            currentState = state
            when (state) {
                State.SEARCHING -> {
                    guidePaint.color = Color.parseColor("#94A3B8") // Gray
                    tlMarkerPaint.color = Color.parseColor("#F59E0B")
                }
                State.FIDUCIALS_FOUND -> {
                    guidePaint.color = Color.parseColor("#3B82F6") // Blue
                    tlMarkerPaint.color = Color.parseColor("#10B981") // Green
                }
                State.PROCESSING -> {
                    guidePaint.color = Color.parseColor("#8B5CF6") // Purple
                }
                State.SUCCESS -> {
                    guidePaint.color = Color.parseColor("#10B981") // Green
                    tlMarkerPaint.color = Color.parseColor("#10B981")
                }
            }
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        // Compute aspect-ratio target box representing A4 sheet (1:1.414)
        val marginH = w * 0.08f
        val sheetW = w - (marginH * 2f)
        val sheetH = sheetW * 1.414f

        val top = (h - sheetH) / 2f
        val bottom = top + sheetH
        val left = marginH
        val right = left + sheetW

        // 1. Draw outer boundary box (dashed or solid)
        canvas.drawRect(left, top, right, bottom, guidePaint)

        // 2. Draw 4 Corner Marker target brackets
        val tlSize = 56f // larger for 14x14mm
        val cornerSize = 40f // 8x8mm

        // Top-Left (Orientation marker)
        canvas.drawRect(left, top, left + tlSize, top + tlSize, tlMarkerPaint)
        canvas.drawText("TL", left + tlSize / 2f, top + tlSize - 12f, textPaint)

        // Top-Right
        canvas.drawRect(right - cornerSize, top, right, top + cornerSize, guidePaint)

        // Bottom-Right
        canvas.drawRect(right - cornerSize, bottom - cornerSize, right, bottom, guidePaint)

        // Bottom-Left
        canvas.drawRect(left, bottom - cornerSize, left + cornerSize, bottom, guidePaint)

        // 3. Draw alignment hint text
        val hint = when (currentState) {
            State.SEARCHING -> "วางกระดาษคำตอบให้เข้ากรอบ (สังเกตมุม TL สีส้ม)"
            State.FIDUCIALS_FOUND -> "พบจุดมาร์กเกอร์ครบ 4 จุด กำลังตรึงระนาบ…"
            State.PROCESSING -> "กำลังอ่านแถบ Timing Tracks และคำนวณผลสอบ…"
            State.SUCCESS -> "✓ ตรวจสอบและบันทึกคะแนนเรียบร้อย!"
        }
        canvas.drawText(hint, w / 2f, top - 24f, textPaint)
    }
}
