package com.pomgrade.omr.omr

/**
 * Configuration and canonical geometry specifications for OMR sheet templates.
 *
 * Credit: จัดทำโดย ครูสุวรรณษา เดชรักษา
 */
object OmrConfig {

    // Canonical Rectified Dimensions (A4 aspect ratio: ~1 : 1.414)
    const val CANONICAL_WIDTH = 1200
    const val CANONICAL_HEIGHT = 1700

    // Timing Tracks (dual-sided row marks)
    const val NUM_ROWS = 25
    const val CHOICES_PER_QUESTION = 4 // A, B, C, D
    val CHOICE_LABELS = listOf("A", "B", "C", "D")

    // Normalized horizontal t-offsets for choices A, B, C, D
    // t in [0.0, 1.0] between Left Track and Right Track
    val CHOICE_T_OFFSETS = listOf(0.18, 0.38, 0.58, 0.78)

    // Bubble radius in canonical pixel space
    const val BUBBLE_RADIUS_PX = 14.0

    // Mark detection thresholds
    const val FILL_THRESHOLD = 0.40       // Minimum dark pixel fill ratio to consider marked
    const val AMBIGUOUS_MARGIN = 0.12     // Difference between highest and 2nd highest choice
    const val MIN_DETECTION_CONFIDENCE = 0.80

    // Student ID Matrix Configuration (5 digits: Columns 0-4, Rows 0-9)
    const val STUDENT_ID_DIGITS = 5
    const val STUDENT_ID_LEFT_X = 220.0
    const val STUDENT_ID_TOP_Y = 180.0
    const val STUDENT_ID_COL_SPACING = 38.0
    const val STUDENT_ID_ROW_SPACING = 28.0

    // Questions Grid Geometry in Canonical Coordinates
    const val QUESTIONS_COL1_START_Y = 560.0
    const val QUESTIONS_COL1_LEFT_X = 160.0
    const val QUESTIONS_COL1_RIGHT_X = 540.0
    const val ROW_HEIGHT_PX = 42.0

    // 2nd Question Column (for 50-question layouts)
    const val QUESTIONS_COL2_LEFT_X = 660.0
    const val QUESTIONS_COL2_RIGHT_X = 1040.0
}

