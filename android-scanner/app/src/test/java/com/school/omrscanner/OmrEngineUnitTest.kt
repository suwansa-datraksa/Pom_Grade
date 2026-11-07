package com.school.omrscanner

import com.school.omrscanner.omr.AnswerEncoder
import com.school.omrscanner.omr.BubbleEvaluator
import com.school.omrscanner.omr.ScanlineInterpolator
import com.school.omrscanner.omr.TimingTrackDetector
import com.school.omrscanner.template.BubbleCoords
import com.school.omrscanner.template.FiducialsGroup
import com.school.omrscanner.template.MarkerCoords
import com.school.omrscanner.template.OptionCoords
import com.school.omrscanner.template.PageSize
import com.school.omrscanner.template.QuestionTemplate
import com.school.omrscanner.template.StudentIdMatrix
import com.school.omrscanner.template.TemplateModel
import com.school.omrscanner.template.TemplateValidator
import com.school.omrscanner.template.TimingBarCoords
import com.school.omrscanner.template.TimingTracksGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.opencv.core.Point

/**
 * Unit Test Suite for Stage 1, Stage 2, and Decision Rules of Android OMR Engine.
 *
 * Credit: จัดทำโดย ครูสุวรรณษา เดชรักษา
 */
class OmrEngineUnitTest {

    private val scanlineInterpolator = ScanlineInterpolator()
    private val bubbleEvaluator = BubbleEvaluator()
    private val answerEncoder = AnswerEncoder()

    private fun createSampleTemplate(
        topLeftSize: Double = 14.0,
        otherMarkerSize: Double = 8.0,
        leftBarW: Double = 5.0,
        rightBarW: Double = 3.0
    ): TemplateModel {
        return TemplateModel(
            templateId = "test_tpl_001",
            version = "6.0.0",
            examId = "EXAM_001",
            examTitle = "ฟิสิกส์ ม.5",
            pageSize = PageSize(210.0, 297.0),
            fiducials = FiducialsGroup(
                topLeft = MarkerCoords(12.0, 12.0, topLeftSize, topLeftSize),
                topRight = MarkerCoords(190.0, 12.0, otherMarkerSize, otherMarkerSize),
                bottomRight = MarkerCoords(190.0, 277.0, otherMarkerSize, otherMarkerSize),
                bottomLeft = MarkerCoords(12.0, 277.0, otherMarkerSize, otherMarkerSize)
            ),
            timingTracks = TimingTracksGroup(
                leftBars = (0 until 20).map { r -> TimingBarCoords(r, 16.0, 105.0 + r * 7.8, leftBarW, 1.6) },
                rightBars = (0 until 20).map { r -> TimingBarCoords(r, 191.0, 105.0 + r * 7.8, rightBarW, 1.6) }
            ),
            studentIdMatrix = StudentIdMatrix(
                digits = 5,
                columns = (0 until 5).map { c ->
                    (0..9).map { d -> BubbleCoords(d, 32.0 + c * 7.2, 40.0 + d * 5.4, 2.1) }
                }
            ),
            questions = (1..20).map { q ->
                QuestionTemplate(
                    questionNumber = q,
                    rowIndex = (q - 1) % 20,
                    options = listOf("A", "B", "C", "D").mapIndexed { idx, ch ->
                        OptionCoords(ch, 30.0 + idx * 8.0, 105.0 + ((q - 1) % 20) * 7.8, 2.3, 0.15 + idx * 0.20)
                    }
                )
            }
        )
    }

    @Test
    fun testTemplateValidation_Valid() {
        val template = createSampleTemplate()
        val result = TemplateValidator.validate(template)
        assertTrue("Valid template must pass validation", result.isValid)
        assertTrue("Errors list must be empty", result.errors.isEmpty())
    }

    @Test
    fun testTemplateValidation_RejectsInvalidFiducial() {
        // Tamper with Top-Left marker size
        val template = createSampleTemplate(topLeftSize = 10.0)
        val result = TemplateValidator.validate(template)
        assertFalse("Template with invalid top-left marker must fail", result.isValid)
        assertTrue(result.errors.any { it.contains("14x14 mm") })
    }

    @Test
    fun testScanlineInterpolator_DualSidedTimingTrackMath() {
        // P_i(t) = (1 - t)*P_L,i + t*P_R,i
        val track = TimingTrackDetector.RowTimingTrack(
            rowIndex = 0,
            pLeft = Point(100.0, 200.0),
            pRight = Point(900.0, 220.0) // Slight 20px vertical tilt
        )

        // Test t = 0.0 (Left edge)
        val p0 = scanlineInterpolator.interpolate(track, 0.0)
        assertEquals(100.0, p0.x, 0.001)
        assertEquals(200.0, p0.y, 0.001)

        // Test t = 1.0 (Right edge)
        val p1 = scanlineInterpolator.interpolate(track, 1.0)
        assertEquals(900.0, p1.x, 0.001)
        assertEquals(220.0, p1.y, 0.001)

        // Test t = 0.50 (Center of question row)
        val pMid = scanlineInterpolator.interpolate(track, 0.50)
        assertEquals(500.0, pMid.x, 0.001)
        assertEquals(210.0, pMid.y, 0.001)
    }

    @Test
    fun testCurvatureDeviationComparison_CurvedVsRigidGrid() {
        // Simulate a cylindrical curved page where middle rows sag downwards by 25 pixels
        val totalRows = 25
        val firstTrack = TimingTrackDetector.RowTimingTrack(0, Point(80.0, 100.0), Point(920.0, 100.0))
        val lastTrack = TimingTrackDetector.RowTimingTrack(24, Point(80.0, 1300.0), Point(920.0, 1300.0))

        // Middle row (row 12) has physical vertical sagging due to paper curl
        val middleRowSagY = 700.0 + 28.0 // 28 px sag
        val curvedMiddleTrack = TimingTrackDetector.RowTimingTrack(12, Point(80.0, middleRowSagY), Point(920.0, middleRowSagY))

        val deviation = scanlineInterpolator.computeCurvatureDeviation(
            curvedMiddleTrack, firstTrack, lastTrack, rowIndex = 12, totalRows = totalRows, t = 0.50
        )

        assertTrue("Curvature deviation must detect significant residual divergence ($deviation px)", deviation > 20.0)

        // When using ScanlineInterpolator, bubble sampling lands precisely on the curved coordinates
        val sampledPt = scanlineInterpolator.interpolate(curvedMiddleTrack, 0.50)
        assertEquals(500.0, sampledPt.x, 0.001)
        assertEquals(middleRowSagY, sampledPt.y, 0.001)
    }

    @Test
    fun testBubbleDecisionRules_Rule1Blank() {
        // Rule 1: f1 < 0.20 -> '-' (Blank)
        val f1 = 0.12
        val f2 = 0.05
        val delta = f1 - f2
        val isBlank = f1 < BubbleEvaluator.THRESHOLD_BLANK
        assertTrue(isBlank)
    }

    @Test
    fun testBubbleDecisionRules_Rule2MultipleMarks() {
        // Rule 2: f2 >= 0.35 && delta < 0.25 -> '*' (Multiple)
        val f1 = 0.52
        val f2 = 0.46
        val delta = f1 - f2 // 0.06 < 0.25
        val isMultiple = f2 >= BubbleEvaluator.THRESHOLD_MULTIPLE_F2 && delta < BubbleEvaluator.THRESHOLD_DELTA
        assertTrue(isMultiple)
    }

    @Test
    fun testBubbleDecisionRules_Rule3FaintErasureOvercome() {
        // Rule 3: f1 >= 0.45 || delta >= 0.25 -> Valid choice with f1
        // Student initially filled B (erased, trace left at 0.28) then firmly filled A (f1 = 0.68)
        val f1 = 0.68
        val f2 = 0.28 // Erasure trace
        val delta = f1 - f2 // 0.40 >= 0.25
        val isValid = f1 >= BubbleEvaluator.THRESHOLD_VALID_F1 || delta >= BubbleEvaluator.THRESHOLD_DELTA
        assertTrue("Must overcome faint pencil erasure cleanly", isValid)
    }

    @Test
    fun testAnswerEncoder_StatusFlags() {
        val studentIdEval = BubbleEvaluator.StudentIdEvaluation(
            studentId = "10001",
            isValid = true,
            perColumnDigits = listOf(1, 0, 0, 0, 1),
            perColumnCertainty = listOf(true, true, true, true, true),
            rawValues = emptyList()
        )

        val clearQuestions = listOf(
            BubbleEvaluator.QuestionEvaluation(1, "A", listOf(0.70, 0.10, 0.05, 0.05), 0.70, 0.10, 0.60, true),
            BubbleEvaluator.QuestionEvaluation(2, "C", listOf(0.08, 0.12, 0.75, 0.05), 0.75, 0.12, 0.63, true),
            BubbleEvaluator.QuestionEvaluation(3, "-", listOf(0.05, 0.08, 0.06, 0.04), 0.08, 0.06, 0.02, true)
        )

        val result = answerEncoder.encode(studentIdEval, clearQuestions)
        assertEquals("10001", result.studentCode)
        assertEquals("AC-", result.answersString)
        assertEquals("OK", result.statusFlag)
        assertEquals(3, result.totalQuestions)
        assertEquals(2, result.answeredCount)
        assertEquals(1, result.blankCount)
    }

    @Test
    fun testAnswerEncoder_FlagsReviewOnUncertain() {
        val studentIdEval = BubbleEvaluator.StudentIdEvaluation(
            studentId = "10002",
            isValid = true,
            perColumnDigits = listOf(1, 0, 0, 0, 2),
            perColumnCertainty = listOf(true, true, true, true, true),
            rawValues = emptyList()
        )

        val questionsWithUncertain = listOf(
            BubbleEvaluator.QuestionEvaluation(1, "A", listOf(0.70, 0.10), 0.70, 0.10, 0.60, true),
            BubbleEvaluator.QuestionEvaluation(2, "?", listOf(0.38, 0.32), 0.38, 0.32, 0.06, false)
        )

        val result = answerEncoder.encode(studentIdEval, questionsWithUncertain)
        assertEquals("A?", result.answersString)
        assertEquals("NEEDS_REVIEW", result.statusFlag)
        assertEquals(1, result.uncertainCount)
    }
}

