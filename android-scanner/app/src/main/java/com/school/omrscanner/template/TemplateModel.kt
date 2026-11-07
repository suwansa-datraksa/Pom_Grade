package com.school.omrscanner.template

import com.google.gson.annotations.SerializedName

/**
 * Data Model for OMR Sheet Templates.
 * Matches the JSON metadata schema exported by Phase 2 Web Template Generator.
 *
 * Credit: จัดทำโดย ครูสุวรรณษา เดชรักษา
 */
data class TemplateModel(
    @SerializedName("template_id")
    val templateId: String,

    @SerializedName("version")
    val version: String,

    @SerializedName("exam_id")
    val examId: String,

    @SerializedName("exam_title")
    val examTitle: String,

    @SerializedName("page_size")
    val pageSize: PageSize,

    @SerializedName("fiducials")
    val fiducials: FiducialsGroup,

    @SerializedName("timing_tracks")
    val timingTracks: TimingTracksGroup,

    @SerializedName("student_id_matrix")
    val studentIdMatrix: StudentIdMatrix,

    @SerializedName("questions")
    val questions: List<QuestionTemplate>,

    @SerializedName("credit")
    val credit: String = "จัดทำโดย ครูสุวรรณษา เดชรักษา"
)

data class PageSize(
    @SerializedName("width_mm")
    val widthMm: Double,

    @SerializedName("height_mm")
    val heightMm: Double
)

data class FiducialsGroup(
    @SerializedName("top_left")
    val topLeft: MarkerCoords,

    @SerializedName("top_right")
    val topRight: MarkerCoords,

    @SerializedName("bottom_right")
    val bottomRight: MarkerCoords,

    @SerializedName("bottom_left")
    val bottomLeft: MarkerCoords
)

data class MarkerCoords(
    @SerializedName("x_mm")
    val xMm: Double,

    @SerializedName("y_mm")
    val yMm: Double,

    @SerializedName("width_mm")
    val widthMm: Double,

    @SerializedName("height_mm")
    val heightMm: Double,

    @SerializedName("center_x_mm")
    val centerXmm: Double? = null,

    @SerializedName("center_y_mm")
    val centerYmm: Double? = null
)

data class TimingTracksGroup(
    @SerializedName("left_bars")
    val leftBars: List<TimingBarCoords>,

    @SerializedName("right_bars")
    val rightBars: List<TimingBarCoords>
)

data class TimingBarCoords(
    @SerializedName("row_index")
    val rowIndex: Int,

    @SerializedName("x_mm")
    val xMm: Double,

    @SerializedName("y_mm")
    val yMm: Double,

    @SerializedName("width_mm")
    val widthMm: Double,

    @SerializedName("height_mm")
    val heightMm: Double,

    @SerializedName("center_x_mm")
    val centerXmm: Double? = null,

    @SerializedName("center_y_mm")
    val centerYmm: Double? = null
)

data class StudentIdMatrix(
    @SerializedName("digits")
    val digits: Int = 5,

    @SerializedName("columns")
    val columns: List<List<BubbleCoords>>
)

data class QuestionTemplate(
    @SerializedName("question_number")
    val questionNumber: Int,

    @SerializedName("row_index")
    val rowIndex: Int,

    @SerializedName("column_index")
    val columnIndex: Int = 0,

    @SerializedName("options")
    val options: List<OptionCoords>
)

data class BubbleCoords(
    @SerializedName("digit")
    val digit: Int,

    @SerializedName("x_mm")
    val xMm: Double,

    @SerializedName("y_mm")
    val yMm: Double,

    @SerializedName("radius_mm")
    val radiusMm: Double = 2.1
)

data class OptionCoords(
    @SerializedName("choice")
    val choice: String,

    @SerializedName("x_mm")
    val xMm: Double,

    @SerializedName("y_mm")
    val yMm: Double,

    @SerializedName("radius_mm")
    val radiusMm: Double = 2.3,

    @SerializedName("t_offset")
    val tOffset: Double
)
