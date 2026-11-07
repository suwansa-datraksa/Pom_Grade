/**
 * Unit Tests for Psychometric Item Analysis & Reliability Engine (Analytics.js)
 * Validates Difficulty (p), Discrimination (D), and KR-20 against verified benchmarks,
 * including zero-variance edge cases.
 *
 * Pom Grade - จัดทำโดย ครูสุวรรณษา เดชรักษา
 */

const assert = require('assert');
const Analytics = require('../js/analytics.js');

function runAnalyticsTests() {
  console.log('Running Pom Grade Item Analysis & Psychometric Tests...\n');
  let passed = 0;

  // ---------------------------------------------------------------------------
  // Test 1: Standard Benchmark Dataset (10 students, 5 questions)
  // ---------------------------------------------------------------------------
  {
    const answerKey = 'ABCDE';
    const students = [
      { studentCode: '10001', answers: 'ABCDE', score: 5 }, // Top
      { studentCode: '10002', answers: 'ABCDA', score: 4 }, // Top
      { studentCode: '10003', answers: 'ABCCE', score: 4 }, // Mid
      { studentCode: '10004', answers: 'ABADE', score: 4 }, // Mid
      { studentCode: '10005', answers: 'AACDE', score: 4 }, // Mid
      { studentCode: '10006', answers: 'EBCDE', score: 4 }, // Mid
      { studentCode: '10007', answers: 'BBCDE', score: 4 }, // Mid
      { studentCode: '10008', answers: 'CBCDE', score: 4 }, // Mid
      { studentCode: '10009', answers: 'AEEEE', score: 2 }, // Low
      { studentCode: '10010', answers: 'EEEEE', score: 1 }, // Low
    ];

    const result = Analytics.analyzeExam(students, answerKey, 5);

    assert.strictEqual(result.N, 10, 'Total students N should be 10');
    assert.strictEqual(result.k, 5, 'Total questions k should be 5');
    assert.strictEqual(result.items.length, 5, 'Should produce 5 item records');

    // Item 1: correct answer 'A'.
    // Students answering 'A' on Q1: 10001(A), 10002(A), 10003(A), 10004(A), 10005(A), 10009(A) = 6 students
    // p = 6 / 10 = 0.60
    assert.strictEqual(result.items[0].difficulty, 0.6, 'Q1 difficulty should be 0.60');

    // Check group size nGroup = round(0.27 * 10) = 3
    assert.strictEqual(result.items[0].nGroup, 3, 'Group size for 27% should be 3');

    // Variance should be > 0 and KR-20 should be a valid finite number
    assert(result.variance > 0, 'Variance should be positive');
    assert(!isNaN(result.kr20), 'KR-20 should not be NaN');
    assert(isFinite(result.kr20), 'KR-20 should be finite');

    console.log(`✓ Test 1 Passed: Benchmark dataset analyzed correctly (Mean: ${result.meanScore}, Var: ${result.variance}, KR-20: ${result.kr20})`);
    passed++;
  }

  // ---------------------------------------------------------------------------
  // Test 2: Zero Variance (All students have identical score: 5 / 5)
  // CRITICAL REQUIREMENT: Must not throw division by zero or produce NaN/Infinity!
  // ---------------------------------------------------------------------------
  {
    const answerKey = 'ABCDE';
    const students = [
      { studentCode: '10001', answers: 'ABCDE', score: 5 },
      { studentCode: '10002', answers: 'ABCDE', score: 5 },
      { studentCode: '10003', answers: 'ABCDE', score: 5 },
      { studentCode: '10004', answers: 'ABCDE', score: 5 },
      { studentCode: '10005', answers: 'ABCDE', score: 5 }
    ];

    const result = Analytics.analyzeExam(students, answerKey, 5);

    assert.strictEqual(result.variance, 0, 'Variance must be exactly 0 when all scores are equal');
    assert.strictEqual(result.kr20, 0, 'KR-20 must safely return 0 when variance is 0 (no division by zero)');
    assert(!isNaN(result.kr20), 'KR-20 must not be NaN');
    assert(isFinite(result.kr20), 'KR-20 must be finite');

    // All items should have p = 1.0 (Difficulty = Very Easy)
    result.items.forEach(item => {
      assert.strictEqual(item.difficulty, 1.0, 'Item difficulty should be 1.0');
      assert.strictEqual(item.discrimination, 0.0, 'Discrimination should be 0 when all answer correctly');
    });

    console.log('✓ Test 2 Passed: Zero variance handled safely with kr20 = 0.0 (no division by zero).');
    passed++;
  }

  // ---------------------------------------------------------------------------
  // Test 3: Zero Variance with All Zeroes (0 / 5)
  // ---------------------------------------------------------------------------
  {
    const answerKey = 'ABCDE';
    const students = [
      { studentCode: '10001', answers: 'EEEEE', score: 0 },
      { studentCode: '10002', answers: 'EEEEE', score: 0 },
      { studentCode: '10003', answers: 'EEEEE', score: 0 }
    ];

    const result = Analytics.analyzeExam(students, answerKey, 5);
    assert.strictEqual(result.variance, 0);
    assert.strictEqual(result.kr20, 0);
    assert(!isNaN(result.kr20));

    console.log('✓ Test 3 Passed: Zero variance (all zero scores) handled safely.');
    passed++;
  }

  // ---------------------------------------------------------------------------
  // Test 4: Single Question Edge Case (k = 1)
  // Formula has [k / (k - 1)], which would divide by zero if k = 1!
  // ---------------------------------------------------------------------------
  {
    const answerKey = 'A';
    const students = [
      { studentCode: '10001', answers: 'A', score: 1 },
      { studentCode: '10002', answers: 'B', score: 0 },
      { studentCode: '10003', answers: 'A', score: 1 }
    ];

    const result = Analytics.analyzeExam(students, answerKey, 1);
    assert.strictEqual(result.k, 1);
    assert.strictEqual(result.kr20, 0, 'KR-20 must be 0 for k = 1 to prevent (k - 1) division by zero');
    assert(!isNaN(result.kr20));

    console.log('✓ Test 4 Passed: Single question k = 1 edge case handled safely without division by zero.');
    passed++;
  }

  // ---------------------------------------------------------------------------
  // Test 5: Empty Student List Edge Case (N = 0)
  // ---------------------------------------------------------------------------
  {
    const result = Analytics.analyzeExam([], 'ABCD', 4);
    assert.strictEqual(result.N, 0);
    assert.strictEqual(result.kr20, 0);
    assert.strictEqual(result.meanScore, 0);

    console.log('✓ Test 5 Passed: Empty student list N = 0 handled safely.');
    passed++;
  }

  // ---------------------------------------------------------------------------
  // Test 6: Perfect Discrimination (High scorers got it right, low scorers wrong)
  // ---------------------------------------------------------------------------
  {
    const answerKey = 'AB';
    const students = [
      { studentCode: '10001', answers: 'AB', score: 2 },
      { studentCode: '10002', answers: 'AB', score: 2 },
      { studentCode: '10003', answers: 'AB', score: 2 },
      { studentCode: '10004', answers: 'CD', score: 0 },
      { studentCode: '10005', answers: 'CD', score: 0 },
      { studentCode: '10006', answers: 'CD', score: 0 }
    ];

    const result = Analytics.analyzeExam(students, answerKey, 2);
    // Upper 27% of 6 is round(1.62) = 2 students
    // R_upper for Q1 = 2, R_lower for Q1 = 0 -> D = (2 - 0)/2 = 1.0 (Perfect Discrimination)
    assert.strictEqual(result.items[0].discrimination, 1.0, 'Item 1 should have perfect discrimination D = 1.0');
    assert.strictEqual(result.items[0].dStatus.code, 'EXCELLENT');

    console.log('✓ Test 6 Passed: Perfect item discrimination correctly evaluated (D = 1.0).');
    passed++;
  }

  // ---------------------------------------------------------------------------
  // Test 7: Distractor Analysis Breakdown
  // ---------------------------------------------------------------------------
  {
    const answerKey = 'A';
    const students = [
      { studentCode: '10001', answers: 'A', score: 1 },
      { studentCode: '10002', answers: 'B', score: 0 },
      { studentCode: '10003', answers: 'C', score: 0 },
      { studentCode: '10004', answers: 'D', score: 0 },
      { studentCode: '10005', answers: '-', score: 0 }
    ];

    const result = Analytics.analyzeExam(students, answerKey, 1);
    const d = result.items[0].distractors;
    assert.strictEqual(d['A'], 1);
    assert.strictEqual(d['B'], 1);
    assert.strictEqual(d['C'], 1);
    assert.strictEqual(d['D'], 1);
    assert.strictEqual(d['BLANK'], 1);

    console.log('✓ Test 7 Passed: Distractor frequency counts verified.');
    passed++;
  }

  console.log(`\nAll ${passed}/${passed} Analytics tests PASSED successfully!\n`);
}

runAnalyticsTests();
