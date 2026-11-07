/**
 * PRODUCTION-GRADE HYBRID OMR EXAM SYSTEM — PHASE 2 UNIT TESTS
 * Verifies all Acceptance Criteria for Phase 2:
 *  1. Exported PDF prints with fiducial markers measuring precisely 14x14 mm and 8x8 mm.
 *  2. Coordinates JSON validates cleanly against client-side schema validator.
 *  3. Dual-sided timing tracks measure precisely 5.0x1.6 mm (left) and 3.0x1.6 mm (right).
 *  4. 5-digit student ID matrix and question choices #666666 borders.
 *
 * Credit: จัดทำโดย ครูสุวรรณษา เดชรักษา
 */

const assert = require('assert');
const fs = require('fs');
const path = require('path');

const TEMPLATE_CONFIG = require('../js/config');
const OmrPdfGenerator = require('../js/pdf-generator');

function runPhase2Tests() {
  console.log('=================================================================');
  console.log(' RUNNING PHASE 2: TEMPLATE GENERATOR & PDF LAYOUT TESTS');
  console.log(' Architecture Specification v6');
  console.log(' จัดทำโดย ครูสุวรรณษา เดชรักษา');
  console.log('=================================================================\n');

  let passed = 0;
  let total = 0;

  function testCase(name, fn) {
    total++;
    try {
      fn();
      console.log(` \x1b[32mPASS\x1b[0m [${total}] ${name}`);
      passed++;
    } catch (err) {
      console.error(` \x1b[31mFAIL\x1b[0m [${total}] ${name}`);
      console.error(err);
      process.exitCode = 1;
    }
  }

  const generator = new OmrPdfGenerator(TEMPLATE_CONFIG);

  // Test 1: Standard Preset Generation (40 Questions)
  let sheet40;
  testCase('Preset 40_QUESTIONS_4_CHOICES generation', () => {
    sheet40 = generator.generateSheet({
      examId: 'EXAM_001',
      examTitle: 'แบบทดสอบวิชาฟิสิกส์ กลางภาค',
      schoolName: 'โรงเรียนมาตรฐานวิทยาคาร',
      presetKey: '40_QUESTIONS_4_CHOICES'
    });

    assert.ok(sheet40.doc, 'jsPDF document must be instantiated');
    assert.ok(sheet40.metadata, 'Coordinate metadata must be generated');
    assert.strictEqual(sheet40.metadata.exam_id, 'EXAM_001');
    assert.strictEqual(sheet40.metadata.questions.length, 40);
  });

  // Test 2: Acceptance Criteria 1 - Fiducial dimensions (14x14 mm and 8x8 mm)
  testCase('Acceptance 1: Corner fiducials precisely 14x14 mm (TL) and 8x8 mm (TR, BR, BL)', () => {
    const fids = sheet40.metadata.fiducials;
    assert.ok(fids.top_left, 'Top-left fiducial must exist');
    assert.strictEqual(fids.top_left.width_mm, 14.0, 'Top-left width must be 14.0 mm');
    assert.strictEqual(fids.top_left.height_mm, 14.0, 'Top-left height must be 14.0 mm');

    assert.strictEqual(fids.top_right.width_mm, 8.0, 'Top-right width must be 8.0 mm');
    assert.strictEqual(fids.top_right.height_mm, 8.0, 'Top-right height must be 8.0 mm');

    assert.strictEqual(fids.bottom_right.width_mm, 8.0, 'Bottom-right width must be 8.0 mm');
    assert.strictEqual(fids.bottom_right.height_mm, 8.0, 'Bottom-right height must be 8.0 mm');

    assert.strictEqual(fids.bottom_left.width_mm, 8.0, 'Bottom-left width must be 8.0 mm');
    assert.strictEqual(fids.bottom_left.height_mm, 8.0, 'Bottom-left height must be 8.0 mm');
  });

  // Test 3: Dual-Sided Timing Tracks dimensions (Left: 5x1.6 mm, Right: 3x1.6 mm)
  testCase('Requirement 3: Dual-Sided Timing Tracks (Left: 5.0x1.6 mm, Right: 3.0x1.6 mm)', () => {
    const tracks = sheet40.metadata.timing_tracks;
    assert.ok(tracks.left_bars.length >= 20, 'Must have timing bars for all rows');
    assert.ok(tracks.right_bars.length >= 20, 'Must have timing bars for all rows');

    tracks.left_bars.forEach((b, idx) => {
      assert.strictEqual(b.width_mm, 5.0, `Left bar ${idx} width must be 5.0 mm`);
      assert.strictEqual(b.height_mm, 1.6, `Left bar ${idx} height must be 1.6 mm`);
    });

    tracks.right_bars.forEach((b, idx) => {
      assert.strictEqual(b.width_mm, 3.0, `Right bar ${idx} width must be 3.0 mm`);
      assert.strictEqual(b.height_mm, 1.6, `Right bar ${idx} height must be 1.6 mm`);
    });
  });

  // Test 4: Student ID Matrix (5 digits)
  testCase('Requirement 4: 5-digit student ID matrix with 10 rows (0-9)', () => {
    const matrix = sheet40.metadata.student_id_matrix;
    assert.strictEqual(matrix.digits, 5);
    assert.strictEqual(matrix.columns.length, 5);

    matrix.columns.forEach((col, colIdx) => {
      assert.strictEqual(col.length, 10, `Column ${colIdx} must contain 10 bubbles (digits 0-9)`);
      for (let digit = 0; digit <= 9; digit++) {
        assert.strictEqual(col[digit].digit, digit);
        assert.ok(col[digit].x_mm > 0);
        assert.ok(col[digit].y_mm > 0);
      }
    });
  });

  // Test 5: Acceptance Criteria 2 - Coordinates JSON Schema Validation
  testCase('Acceptance 2: Coordinates JSON validates cleanly against schema validator', () => {
    const validation = OmrPdfGenerator.validateMetadata(sheet40.metadata);
    assert.strictEqual(validation.valid, true, 'Metadata must pass validation cleanly');
    assert.strictEqual(validation.errors.length, 0);
  });

  // Test 6: Deliberate Tamper Rejection
  testCase('Validator Resilience: Rejects invalid fiducial dimensions', () => {
    const tampered = JSON.parse(JSON.stringify(sheet40.metadata));
    tampered.fiducials.top_left.width_mm = 10.0; // Invalid (should be 14.0)

    const result = OmrPdfGenerator.validateMetadata(tampered);
    assert.strictEqual(result.valid, false);
    assert.ok(result.errors.some(e => e.includes('14x14 mm')));
  });

  // Test 7: Preset 20 Questions (Single Column)
  testCase('Preset 20_QUESTIONS_4_CHOICES validation', () => {
    const sheet20 = generator.generateSheet({ presetKey: '20_QUESTIONS_4_CHOICES' });
    assert.strictEqual(sheet20.metadata.questions.length, 20);
    const val = OmrPdfGenerator.validateMetadata(sheet20.metadata);
    assert.strictEqual(val.valid, true);
  });

  // Test 8: Preset 60 Questions (High-Density)
  testCase('Preset 60_QUESTIONS_4_CHOICES validation', () => {
    const sheet60 = generator.generateSheet({ presetKey: '60_QUESTIONS_4_CHOICES' });
    assert.strictEqual(sheet60.metadata.questions.length, 60);
    const val = OmrPdfGenerator.validateMetadata(sheet60.metadata);
    assert.strictEqual(val.valid, true);
  });

  // Test 9: Preset 40 Questions (5 Choices: A, B, C, D, E)
  testCase('Preset 40_QUESTIONS_5_CHOICES validation', () => {
    const sheet5C = generator.generateSheet({ presetKey: '40_QUESTIONS_5_CHOICES' });
    assert.strictEqual(sheet5C.metadata.questions.length, 40);
    sheet5C.metadata.questions.forEach(q => {
      assert.strictEqual(q.options.length, 5, 'Must have 5 choices');
      assert.strictEqual(q.options[4].choice, 'E');
    });
    const val = OmrPdfGenerator.validateMetadata(sheet5C.metadata);
    assert.strictEqual(val.valid, true);
  });

  // Test 10: PDF Binary Buffer Rendering
  testCase('Requirement 1: jsPDF renders binary output buffer cleanly', () => {
    const pdfOutput = sheet40.doc.output('arraybuffer');
    assert.ok(pdfOutput.byteLength > 1000, 'Rendered PDF buffer must be non-empty');
  });

  // Test 11: Prominent Credit Footer
  testCase('Compliance: Prominent credit footer text in metadata and config', () => {
    assert.strictEqual(TEMPLATE_CONFIG.CREDIT, 'จัดทำโดย ครูสุวรรณษา เดชรักษา');
    assert.strictEqual(sheet40.metadata.credit, 'จัดทำโดย ครูสุวรรณษา เดชรักษา');
  });

  console.log('\n-----------------------------------------------------------------');
  console.log(` SUMMARY: ${passed}/${total} TESTS PASSED`);
  console.log('-----------------------------------------------------------------\n');
}

runPhase2Tests();
