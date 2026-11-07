package com.school.omrscanner.template

import kotlin.math.abs

/**
 * Validates OMR Template schema and geometric constraints according to Architecture v6.
 *
 * Acceptance Criteria Verification:
 *  - Top-left fiducial marker must be precisely 14x14 mm.
 *  - Other 3 corner markers must be precisely 8x8 mm.
 *  - Left timing tracks must be 5.0x1.6 mm.
 *  - Right timing tracks must be 3.0x1.6 mm.
 *
 * Credit: จัดทำโดย ครูสุวรรณษา เดชรักษา
 */
object TemplateValidator {

    private const val TOLERANCE_MM = 0.25

    data class ValidationResult(
        val isValid: Boolean,
        val errors: List<String>
    )

    fun validate(template: TemplateModel): ValidationResult {
        val errors = mutableListOf<String>()

        // 1. Validate Fiducials
        val fids = template.fiducials
        if (abs(fids.topLeft.widthMm - 14.0) > TOLERANCE_MM || abs(fids.topLeft.heightMm - 14.0) > TOLERANCE_MM) {
            errors.add("Top-left fiducial must be 14x14 mm (got ${fids.topLeft.widthMm}x${fids.topLeft.heightMm})")
        }

        listOf(
            "top_right" to fids.topRight,
            "bottom_right" to fids.bottomRight,
            "bottom_left" to fids.bottomLeft
        ).forEach { (name, marker) ->
            if (abs(marker.widthMm - 8.0) > TOLERANCE_MM || abs(marker.heightMm - 8.0) > TOLERANCE_MM) {
                errors.add("$name fiducial must be 8x8 mm (got ${marker.widthMm}x${marker.heightMm})")
            }
        }

        // 2. Validate Timing Tracks
        val tracks = template.timingTracks
        if (tracks.leftBars.isEmpty()) {
            errors.add("Left timing bars collection cannot be empty")
        } else {
            tracks.leftBars.forEachIndexed { idx, bar ->
                if (abs(bar.widthMm - 5.0) > TOLERANCE_MM || abs(bar.heightMm - 1.6) > TOLERANCE_MM) {
                    errors.add("Left timing bar at row $idx must be 5.0x1.6 mm (got ${bar.widthMm}x${bar.heightMm})")
                }
            }
        }

        if (tracks.rightBars.isEmpty()) {
            errors.add("Right timing bars collection cannot be empty")
        } else {
            tracks.rightBars.forEachIndexed { idx, bar ->
                if (abs(bar.widthMm - 3.0) > TOLERANCE_MM || abs(bar.heightMm - 1.6) > TOLERANCE_MM) {
                    errors.add("Right timing bar at row $idx must be 3.0x1.6 mm (got ${bar.widthMm}x${bar.heightMm})")
                }
            }
        }

        // 3. Validate Student ID Matrix
        val matrix = template.studentIdMatrix
        if (matrix.digits != 5 || matrix.columns.size != 5) {
            errors.add("Student ID matrix must have exactly 5 digits (got ${matrix.columns.size} columns)")
        } else {
            matrix.columns.forEachIndexed { colIdx, col ->
                if (col.size != 10) {
                    errors.add("Student ID column $colIdx must contain 10 bubble positions (digits 0-9)")
                }
            }
        }

        // 4. Validate Questions
        if (template.questions.isEmpty()) {
            errors.add("Template must contain at least 1 question")
        } else {
            template.questions.forEach { q ->
                if (q.options.size < 2) {
                    errors.add("Question ${q.questionNumber} must have at least 2 choice options")
                }
                q.options.forEach { opt ->
                    if (opt.tOffset < 0.0 || opt.tOffset > 1.0) {
                        errors.add("Question ${q.questionNumber} option ${opt.choice} has invalid t_offset (${opt.tOffset}). Must be in [0, 1]")
                    }
                }
            }
        }

        return ValidationResult(
            isValid = errors.isEmpty(),
            errors = errors
        )
    }
}
