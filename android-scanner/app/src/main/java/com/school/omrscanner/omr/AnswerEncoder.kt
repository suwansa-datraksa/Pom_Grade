package com.school.omrscanner.omr

/**
 * Encodes decoded student answers and metrics into the standard payload format.
 * Strictly adheres to Architecture Specification v6 format:
 *
 * Recognized symbols:
 *  - 'A', 'B', 'C', 'D', 'E' : Valid choices
 *  - '-'                      : Blank / Unanswered
 *  - '*'                      : Multiple marks
 *  - '?'                      : Uncertain / Ambiguous
 *
 * Status flags:
 *  - 'OK'           : Clear answers and recognized student ID
 *  - 'NEEDS_REVIEW' : Contains '?' or '*' or paper curved/missing rows
 *  - 'BAD_FORMAT'   : Incomplete student ID or corrupted format
 *
 * Credit: จัดทำโดย ครูสุวรรณษา เดชรักษา
 */
class AnswerEncoder {

    data class EncodedResult(
        val studentCode: String,
        val answersString: String,
        val statusFlag: String, // "OK", "NEEDS_REVIEW", "BAD_FORMAT"
        val totalQuestions: Int,
        val answeredCount: Int,
        val blankCount: Int,
        val multipleCount: Int,
        val uncertainCount: Int,
        val meanConfidence: Double
    )

    /**
     * Encodes evaluation outputs into a standardized result.
     */
    fun encode(
        studentIdEval: BubbleEvaluator.StudentIdEvaluation,
        questionEvals: List<BubbleEvaluator.QuestionEvaluation>,
        timingResult: TimingTrackDetector.TrackExtractionResult? = null
    ): EncodedResult {
        val studentCode = studentIdEval.studentId ?: "00000"

        val answersBuilder = StringBuilder()
        var answered = 0
        var blanks = 0
        var multiples = 0
        var uncertains = 0
        var sumConfidence = 0.0

        for (q in questionEvals) {
            val ans = q.recognizedAnswer
            answersBuilder.append(ans)

            when (ans) {
                "-" -> blanks++
                "*" -> multiples++
                "?" -> uncertains++
                else -> answered++
            }

            // Confidence metric based on delta margin
            val conf = if (ans != "-" && ans != "*" && ans != "?") {
                (q.delta / 0.50).coerceIn(0.50, 1.0)
            } else if (ans == "-") {
                (1.0 - q.f1).coerceIn(0.50, 1.0)
            } else {
                0.20
            }
            sumConfidence += conf
        }

        val totalQ = questionEvals.size
        val meanConf = if (totalQ > 0) sumConfidence / totalQ else 0.0

        // Determine Status Flag
        val status = when {
            !studentIdEval.isValid -> "BAD_FORMAT"
            uncertains > 0 -> "NEEDS_REVIEW"
            multiples > 0 -> "NEEDS_REVIEW"
            timingResult != null && !timingResult.isValid -> "NEEDS_REVIEW"
            timingResult != null && timingResult.isCurvedCylindrical -> "OK" // Successfully corrected
            else -> "OK"
        }

        return EncodedResult(
            studentCode = studentCode,
            answersString = answersBuilder.toString(),
            statusFlag = status,
            totalQuestions = totalQ,
            answeredCount = answered,
            blankCount = blanks,
            multipleCount = multiples,
            uncertainCount = uncertains,
            meanConfidence = meanConf
        )
    }
}

