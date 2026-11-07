/**
 * Psychometric Item Analysis & Reliability Engine
 * Computes:
 *  - Item Difficulty (p = R / N)
 *  - Item Discrimination (D = (R_upper - R_lower) / n) using Top/Bottom 27%
 *  - Test Reliability (KR-20 = [k / (k - 1)] * [1 - sum(p * q) / var_total])
 *  - Distractor Analysis (Option choice frequencies)
 *
 * Pom Grade - จัดทำโดย ครูสุวรรณษา เดชรักษา
 */

(function (window) {
  'use strict';

  const Analytics = {
    /**
     * Perform complete psychometric analysis for an exam
     * @param {Array<Object>} studentRecords Array of { studentCode, answers, score }
     * @param {string} answerKey String of correct answers (e.g., "ABCDA...")
     * @param {number} totalQuestions Number of questions (k)
     * @returns {Object} Complete analysis report
     */
    analyzeExam(studentRecords, answerKey, totalQuestions) {
      const N = studentRecords ? studentRecords.length : 0;
      const k = totalQuestions || (answerKey ? answerKey.length : 0);

      if (N === 0 || k === 0 || !answerKey) {
        return {
          N: 0,
          k: k,
          meanScore: 0,
          medianScore: 0,
          stdDev: 0,
          variance: 0,
          minScore: 0,
          maxScore: 0,
          kr20: 0,
          items: []
        };
      }

      // 1. Grade each student's responses if not already graded
      const gradedExaminees = studentRecords.map(student => {
        const ans = String(student.answers || '').padEnd(k, '-');
        let calculatedScore = 0;
        const itemScores = [];

        for (let q = 0; q < k; q++) {
          const studentChoice = ans[q] ? ans[q].toUpperCase() : '-';
          const correctChoice = answerKey[q] ? answerKey[q].toUpperCase() : '';
          const isCorrect = (studentChoice !== '-' && studentChoice === correctChoice) ? 1 : 0;
          calculatedScore += isCorrect;
          itemScores.push({
            choice: studentChoice,
            isCorrect: isCorrect
          });
        }

        const finalScore = (typeof student.score === 'number' && !isNaN(student.score))
          ? student.score
          : calculatedScore;

        return {
          studentCode: student.studentCode,
          score: finalScore,
          answers: ans,
          itemScores: itemScores
        };
      });

      // 2. Score statistics (Mean, Variance, StdDev, Min, Max)
      const scores = gradedExaminees.map(e => e.score);
      const sumScores = scores.reduce((acc, s) => acc + s, 0);
      const meanScore = sumScores / N;

      // Calculate population variance of total scores: var = (1/N) * sum((X - mean)^2)
      // and sample variance if N > 1
      let sumSquaredDiffs = 0;
      for (let s of scores) {
        sumSquaredDiffs += Math.pow(s - meanScore, 2);
      }
      const variance = (N > 1) ? (sumSquaredDiffs / (N - 1)) : 0;
      const stdDev = Math.sqrt(variance);

      const minScore = Math.min(...scores);
      const maxScore = Math.max(...scores);

      // Median score
      const sortedScores = [...scores].sort((a, b) => a - b);
      const mid = Math.floor(sortedScores.length / 2);
      const medianScore = (sortedScores.length % 2 !== 0)
        ? sortedScores[mid]
        : ((sortedScores[mid - 1] + sortedScores[mid]) / 2);

      // 3. Partition into Upper & Lower 27% groups for Discrimination Index
      // Sort examinees descending by total score
      const rankedExaminees = [...gradedExaminees].sort((a, b) => b.score - a.score);
      const nGroup = Math.max(1, Math.round(0.27 * N));
      const upperGroup = rankedExaminees.slice(0, nGroup);
      const lowerGroup = rankedExaminees.slice(Math.max(0, N - nGroup));

      // 4. Item Analysis (Difficulty p, Discrimination D, Distractors)
      const items = [];
      let sumPq = 0;

      for (let q = 0; q < k; q++) {
        const correctChoice = answerKey[q] ? answerKey[q].toUpperCase() : '';

        // Count correct in total population
        let R = 0;
        const distractorCounts = { 'A': 0, 'B': 0, 'C': 0, 'D': 0, 'E': 0, 'BLANK': 0, 'OTHER': 0 };

        for (const examinee of gradedExaminees) {
          const choice = examinee.itemScores[q] ? examinee.itemScores[q].choice : '-';
          if (choice === correctChoice) {
            R++;
          }

          if (choice === '-' || choice === '') {
            distractorCounts['BLANK']++;
          } else if (distractorCounts.hasOwnProperty(choice)) {
            distractorCounts[choice]++;
          } else {
            distractorCounts['OTHER']++;
          }
        }

        // Item Difficulty: p = R / N
        const p = N > 0 ? (R / N) : 0;
        const qVal = 1 - p;
        sumPq += (p * qVal);

        // Count correct in Upper and Lower groups
        let R_upper = 0;
        for (const ex of upperGroup) {
          if (ex.itemScores[q] && ex.itemScores[q].choice === correctChoice) {
            R_upper++;
          }
        }

        let R_lower = 0;
        for (const ex of lowerGroup) {
          if (ex.itemScores[q] && ex.itemScores[q].choice === correctChoice) {
            R_lower++;
          }
        }

        // Item Discrimination: D = (R_upper - R_lower) / nGroup
        const D = nGroup > 0 ? ((R_upper - R_lower) / nGroup) : 0;

        items.push({
          itemNumber: q + 1,
          correctChoice: correctChoice,
          correctCount: R,
          totalExaminees: N,
          difficulty: Number(p.toFixed(4)),
          discrimination: Number(D.toFixed(4)),
          pStatus: this.classifyDifficulty(p),
          dStatus: this.classifyDiscrimination(D),
          distractors: distractorCounts,
          rUpper: R_upper,
          rLower: R_lower,
          nGroup: nGroup
        });
      }

      // 5. Reliability Coefficient: KR-20
      // Formula: KR-20 = [k / (k - 1)] * [1 - sum(p * q) / var_total]
      // CRITICAL: Handle var_total = 0 or k <= 1 gracefully to prevent division by zero / NaN
      let kr20 = 0;
      if (k > 1 && variance > 0.000001) {
        const ratioK = k / (k - 1);
        const itemVarComponent = 1 - (sumPq / variance);
        kr20 = ratioK * itemVarComponent;
        
        // Clamp to [-1.0, 1.0] for theoretical sanity
        if (isNaN(kr20) || !isFinite(kr20)) {
          kr20 = 0;
        } else {
          kr20 = Math.max(-1.0, Math.min(1.0, kr20));
        }
      } else {
        // Variance is 0 (all scores identical) or only 1 item -> reliability is 0
        kr20 = 0.0;
      }

      return {
        N,
        k,
        meanScore: Number(meanScore.toFixed(2)),
        medianScore: Number(medianScore.toFixed(2)),
        variance: Number(variance.toFixed(4)),
        stdDev: Number(stdDev.toFixed(2)),
        minScore,
        maxScore,
        sumPq: Number(sumPq.toFixed(4)),
        kr20: Number(kr20.toFixed(4)),
        kr20Rating: this.classifyReliability(kr20),
        items
      };
    },

    /**
     * Classify Item Difficulty (p)
     */
    classifyDifficulty(p) {
      if (p >= 0.80) return { label: 'ง่ายมาก', code: 'VERY_EASY', color: '#10B981' };
      if (p >= 0.60) return { label: 'ค่อนข้างง่าย', code: 'EASY', color: '#3B82F6' };
      if (p >= 0.40) return { label: 'ปานกลาง (เหมาะสม)', code: 'MODERATE', color: '#10B981' };
      if (p >= 0.20) return { label: 'ค่อนข้างยาก', code: 'DIFFICULT', color: '#F59E0B' };
      return { label: 'ยากมาก', code: 'VERY_DIFFICULT', color: '#EF4444' };
    },

    /**
     * Classify Item Discrimination (D)
     */
    classifyDiscrimination(D) {
      if (D >= 0.40) return { label: 'จำแนกได้ดีมาก', code: 'EXCELLENT', color: '#10B981' };
      if (D >= 0.30) return { label: 'จำแนกได้ดี', code: 'GOOD', color: '#3B82F6' };
      if (D >= 0.20) return { label: 'จำแนกพอใช้', code: 'FAIR', color: '#F59E0B' };
      return { label: 'จำแนกไม่ดี (ควรปรับปรุง)', code: 'POOR', color: '#EF4444' };
    },

    /**
     * Classify Reliability Coefficient (KR-20)
     */
    classifyReliability(r) {
      if (r >= 0.80) return { label: 'ความเชื่อมั่นสูงมาก (ดีเยี่ยม)', color: '#10B981' };
      if (r >= 0.70) return { label: 'ความเชื่อมั่นสูง (ใช้ได้ดี)', color: '#3B82F6' };
      if (r >= 0.60) return { label: 'ความเชื่อมั่นปานกลาง (ยอมรับได้)', color: '#F59E0B' };
      return { label: 'ความเชื่อมั่นต่ำ (ควรทบทวนข้อสอบ)', color: '#EF4444' };
    }
  };

  window.PomGradeAnalytics = Analytics;

  if (typeof module !== 'undefined' && module.exports) {
    module.exports = Analytics;
  }
})(typeof window !== 'undefined' ? window : global);

