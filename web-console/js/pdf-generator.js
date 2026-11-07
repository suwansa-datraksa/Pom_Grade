/**
 * PRODUCTION-GRADE HYBRID OMR EXAM SYSTEM — PDF GENERATOR ENGINE
 * Architecture Specification v6 (Phase 2)
 *
 * Responsibilities:
 *  - High-precision vector PDF generation via jsPDF at 100% scale (no margin clipping)
 *  - 4 asymmetric corner fiducial markers (Top-Left: 14x14 mm, Others: 8x8 mm)
 *  - Dual-sided timing tracks (Left: 5.0x1.6 mm, Right: 3.0x1.6 mm)
 *  - Standard 5-digit student ID matrix & configurable question choices (#666666 borders)
 *  - Export coordinate metadata JSON matching TemplateValidator.kt schema
 *
 * Credit: จัดทำโดย ครูสุวรรณษา เดชรักษา
 */

class OmrPdfGenerator {

  constructor(config = TEMPLATE_CONFIG) {
    this.config = config;
  }

  /**
   * Generates a complete OMR Sheet document and metadata.
   *
   * @param {Object} options
   * @param {string} options.examId e.g. "EXAM_001"
   * @param {string} options.examTitle e.g. "แบบทดสอบวิชาฟิสิกส์ กลางภาคเรียนที่ 2"
   * @param {string} options.presetKey e.g. "40_QUESTIONS_4_CHOICES"
   * @param {string} options.schoolName e.g. "โรงเรียนมาตรฐานวิทยาคาร"
   * @returns {{ doc: jsPDF, metadata: Object }}
   */
  generateSheet(options = {}) {
    const examId = options.examId || 'EXAM_001';
    const examTitle = options.examTitle || 'แบบทดสอบวัดผลการเรียนรู้มาตรฐาน';
    const presetKey = options.presetKey || '40_QUESTIONS_4_CHOICES';
    const schoolName = options.schoolName || 'ระบบตรวจข้อสอบ OMR มาตรฐาน (v6)';

    const preset = this.config.PRESETS[presetKey] || this.config.PRESETS['40_QUESTIONS_4_CHOICES'];

    // Instantiate jsPDF: Unit in mm, Format A4 [210 x 297]
    const { jsPDF } = (typeof window !== 'undefined' && window.jspdf) ? window.jspdf : require('jspdf');
    const doc = new jsPDF({
      orientation: 'portrait',
      unit: 'mm',
      format: 'a4',
      compress: true
    });

    // Metadata payload for Android Scanner TemplateValidator.kt
    const metadata = {
      template_id: `tpl_${examId.toLowerCase()}_${presetKey.toLowerCase()}`,
      version: '6.0.0',
      exam_id: examId,
      exam_title: examTitle,
      created_at: new Date().toISOString(),
      credit: this.config.CREDIT,
      page_size: {
        width_mm: this.config.PAGE.WIDTH_MM,
        height_mm: this.config.PAGE.HEIGHT_MM
      },
      fiducials: {},
      timing_tracks: {
        left_bars: [],
        right_bars: []
      },
      student_id_matrix: {
        digits: this.config.STUDENT_ID.DIGITS,
        columns: []
      },
      questions: []
    };

    // 1. Draw Corner Fiducials
    this.drawFiducials(doc, metadata);

    // 2. Draw Header Banner & Metadata Labels
    this.drawHeader(doc, examId, examTitle, schoolName);

    // 3. Draw Student ID Grid
    this.drawStudentIdGrid(doc, metadata);

    // 4. Draw Timing Tracks & Question Rows
    this.drawQuestionsAndTimingTracks(doc, metadata, preset);

    // 5. Draw Prominent Credit Footer
    this.drawFooter(doc);

    return { doc, metadata };
  }

  /**
   * 1. Corner Fiducials:
   * Top-Left: 14x14 mm (Orientation Anchor)
   * Top-Right: 8x8 mm
   * Bottom-Right: 8x8 mm
   * Bottom-Left: 8x8 mm
   */
  drawFiducials(doc, metadata) {
    const marginX = this.config.FIDUCIALS.MARGIN_X_MM;
    const marginY = this.config.FIDUCIALS.MARGIN_Y_MM;
    const pageWidth = this.config.PAGE.WIDTH_MM;
    const pageHeight = this.config.PAGE.HEIGHT_MM;

    doc.setFillColor(0, 0, 0); // Solid black

    // Top-Left (14 x 14 mm)
    const tlW = this.config.FIDUCIALS.TOP_LEFT.WIDTH_MM;
    const tlH = this.config.FIDUCIALS.TOP_LEFT.HEIGHT_MM;
    doc.rect(marginX, marginY, tlW, tlH, 'F');
    metadata.fiducials.top_left = {
      x_mm: marginX,
      y_mm: marginY,
      width_mm: tlW,
      height_mm: tlH,
      center_x_mm: marginX + (tlW / 2),
      center_y_mm: marginY + (tlH / 2)
    };

    // Top-Right (8 x 8 mm)
    const trW = this.config.FIDUCIALS.STANDARD.WIDTH_MM;
    const trH = this.config.FIDUCIALS.STANDARD.HEIGHT_MM;
    const trX = pageWidth - marginX - trW;
    const trY = marginY;
    doc.rect(trX, trY, trW, trH, 'F');
    metadata.fiducials.top_right = {
      x_mm: trX,
      y_mm: trY,
      width_mm: trW,
      height_mm: trH,
      center_x_mm: trX + (trW / 2),
      center_y_mm: trY + (trH / 2)
    };

    // Bottom-Right (8 x 8 mm)
    const brW = this.config.FIDUCIALS.STANDARD.WIDTH_MM;
    const brH = this.config.FIDUCIALS.STANDARD.HEIGHT_MM;
    const brX = pageWidth - marginX - brW;
    const brY = pageHeight - marginY - brH;
    doc.rect(brX, brY, brW, brH, 'F');
    metadata.fiducials.bottom_right = {
      x_mm: brX,
      y_mm: brY,
      width_mm: brW,
      height_mm: brH,
      center_x_mm: brX + (brW / 2),
      center_y_mm: brY + (brH / 2)
    };

    // Bottom-Left (8 x 8 mm)
    const blW = this.config.FIDUCIALS.STANDARD.WIDTH_MM;
    const blH = this.config.FIDUCIALS.STANDARD.HEIGHT_MM;
    const blX = marginX;
    const blY = pageHeight - marginY - blH;
    doc.rect(blX, blY, blW, blH, 'F');
    metadata.fiducials.bottom_left = {
      x_mm: blX,
      y_mm: blY,
      width_mm: blW,
      height_mm: blH,
      center_x_mm: blX + (blW / 2),
      center_y_mm: blY + (blH / 2)
    };
  }

  /**
   * 2. Header and exam instructions
   */
  drawHeader(doc, examId, examTitle, schoolName) {
    doc.setFont('helvetica', 'bold');
    doc.setFontSize(14);
    doc.setTextColor(30, 58, 138); // Deep Navy
    doc.text(schoolName, 32.0, 18.0);

    doc.setFontSize(10);
    doc.setTextColor(15, 23, 42); // Slate 900
    doc.text(`Exam ID: ${examId} | ${examTitle}`, 32.0, 24.0);

    doc.setFont('helvetica', 'normal');
    doc.setFontSize(8);
    doc.setTextColor(100, 116, 139); // Slate 500
    doc.text('Instructions: Use 2B pencil or black pen to fill bubbles completely. Do not fold or smudge page.', 32.0, 29.0);

    // Decorative divider line
    doc.setDrawColor(203, 213, 225);
    doc.setLineWidth(0.3);
    doc.line(32.0, 32.0, 194.0, 32.0);
  }

  /**
   * 3. 5-Digit Student ID Bubble Matrix
   */
  drawStudentIdGrid(doc, metadata) {
    const startX = this.config.STUDENT_ID.START_X_MM;
    const startY = this.config.STUDENT_ID.START_Y_MM;
    const colSpacing = this.config.STUDENT_ID.COL_SPACING_MM;
    const rowSpacing = this.config.STUDENT_ID.ROW_SPACING_MM;
    const radius = this.config.STUDENT_ID.BUBBLE_RADIUS_MM;

    doc.setFont('helvetica', 'bold');
    doc.setFontSize(8.5);
    doc.setTextColor(30, 58, 138);
    doc.text('STUDENT ID (5 Digits)', startX, startY - 2.0);

    doc.setDrawColor(102, 102, 102); // #666666 neutral gray
    doc.setLineWidth(this.config.BUBBLES.LINE_WIDTH_MM);

    for (let c = 0; c < this.config.STUDENT_ID.DIGITS; c++) {
      const colX = startX + (c * colSpacing);
      const colBubbles = [];

      // Column header box for student to write digit
      doc.setDrawColor(148, 163, 184);
      doc.rect(colX - 2.8, startY + 1.0, 5.6, 6.0);

      // Bubbles 0 to 9
      for (let digit = 0; digit <= 9; digit++) {
        const bubbleY = startY + 12.0 + (digit * rowSpacing);

        doc.setDrawColor(102, 102, 102);
        doc.setFillColor(255, 255, 255);
        doc.circle(colX, bubbleY, radius, 'FD');

        doc.setFont('helvetica', 'normal');
        doc.setFontSize(6.5);
        doc.setTextColor(85, 85, 85);
        doc.text(String(digit), colX - 0.9, bubbleY + 0.9);

        colBubbles.push({
          digit: digit,
          x_mm: colX,
          y_mm: bubbleY,
          radius_mm: radius
        });
      }

      metadata.student_id_matrix.columns.push(colBubbles);
    }
  }

  /**
   * 4. Dual-Sided Timing Tracks & Question Rows
   * Left Bar: 5.0 x 1.6 mm
   * Right Bar: 3.0 x 1.6 mm
   * Positioned precisely at row mid-line
   */
  drawQuestionsAndTimingTracks(doc, metadata, preset) {
    const numQuestions = preset.questions;
    const choices = preset.choices;
    const numCols = preset.columns;
    const rowsPerCol = Math.ceil(numQuestions / numCols);
    const startY = preset.startY;
    const rowHeight = preset.rowHeight;
    const choiceSpacing = preset.choiceSpacing;
    const bubbleRadius = this.config.BUBBLES.RADIUS_MM;

    const leftBarX = this.config.TIMING_TRACKS.MARGIN_LEFT_X_MM;
    const rightBarX = this.config.TIMING_TRACKS.MARGIN_RIGHT_X_MM;
    const leftBarW = this.config.TIMING_TRACKS.LEFT_BAR.WIDTH_MM;
    const leftBarH = this.config.TIMING_TRACKS.LEFT_BAR.HEIGHT_MM;
    const rightBarW = this.config.TIMING_TRACKS.RIGHT_BAR.WIDTH_MM;
    const rightBarH = this.config.TIMING_TRACKS.RIGHT_BAR.HEIGHT_MM;

    doc.setFillColor(0, 0, 0); // Solid black timing tracks

    // Draw timing bars for each row
    for (let r = 0; r < rowsPerCol; r++) {
      const rowCenterY = startY + (r * rowHeight);
      const barTopLeftY = rowCenterY - (leftBarH / 2);
      const barTopRightY = rowCenterY - (rightBarH / 2);

      // Left bar (5.0 x 1.6 mm)
      doc.rect(leftBarX, barTopLeftY, leftBarW, leftBarH, 'F');
      metadata.timing_tracks.left_bars.push({
        row_index: r,
        x_mm: leftBarX,
        y_mm: barTopLeftY,
        width_mm: leftBarW,
        height_mm: leftBarH,
        center_x_mm: leftBarX + (leftBarW / 2),
        center_y_mm: rowCenterY
      });

      // Right bar (3.0 x 1.6 mm)
      doc.rect(rightBarX, barTopRightY, rightBarW, rightBarH, 'F');
      metadata.timing_tracks.right_bars.push({
        row_index: r,
        x_mm: rightBarX,
        y_mm: barTopRightY,
        width_mm: rightBarW,
        height_mm: rightBarH,
        center_x_mm: rightBarX + (rightBarW / 2),
        center_y_mm: rowCenterY
      });
    }

    // Column horizontal start positions
    const colStarts = numCols === 1 ? [28.0] : [26.0, 114.0];

    // Draw Questions
    for (let q = 1; q <= numQuestions; q++) {
      const colIdx = Math.floor((q - 1) / rowsPerCol);
      const rowInCol = (q - 1) % rowsPerCol;
      const rowCenterY = startY + (rowInCol * rowHeight);
      const colStartX = colStarts[colIdx];

      // Question Number Label
      doc.setFont('helvetica', 'bold');
      doc.setFontSize(8);
      doc.setTextColor(30, 58, 138);
      doc.text(String(q).padStart(2, ' '), colStartX, rowCenterY + 1.0);

      const qOptions = [];
      const leftP = metadata.timing_tracks.left_bars[rowInCol];
      const rightP = metadata.timing_tracks.right_bars[rowInCol];

      // Choice Bubbles
      for (let cIdx = 0; cIdx < choices.length; cIdx++) {
        const choiceLabel = choices[cIdx];
        const bubbleX = colStartX + 9.0 + (cIdx * choiceSpacing);

        doc.setDrawColor(102, 102, 102); // #666666
        doc.setFillColor(255, 255, 255);
        doc.setLineWidth(this.config.BUBBLES.LINE_WIDTH_MM);
        doc.circle(bubbleX, rowCenterY, bubbleRadius, 'FD');

        doc.setFont('helvetica', 'normal');
        doc.setFontSize(6.5);
        doc.setTextColor(85, 85, 85);
        doc.text(choiceLabel, bubbleX - 1.0, rowCenterY + 0.9);

        // Calculate normalized t-offset between Left Track and Right Track
        const tOffset = (bubbleX - leftP.center_x_mm) / (rightP.center_x_mm - leftP.center_x_mm);

        qOptions.push({
          choice: choiceLabel,
          x_mm: bubbleX,
          y_mm: rowCenterY,
          radius_mm: bubbleRadius,
          t_offset: parseFloat(tOffset.toFixed(4))
        });
      }

      metadata.questions.push({
        question_number: q,
        row_index: rowInCol,
        column_index: colIdx,
        options: qOptions
      });
    }
  }

  /**
   * 5. Prominent Credit Footer
   */
  drawFooter(doc) {
    doc.setFont('helvetica', 'italic');
    doc.setFontSize(8);
    doc.setTextColor(100, 116, 139);
    doc.text(this.config.CREDIT, 105.0, 289.0, { align: 'center' });
  }

  /**
   * Validates metadata according to acceptance criteria.
   *
   * @param {Object} metadata
   * @returns {{ valid: boolean, errors: string[] }}
   */
  static validateMetadata(metadata) {
    const errors = [];

    if (!metadata || !metadata.fiducials) {
      errors.push('Missing fiducials in metadata');
      return { valid: false, errors };
    }

    const { top_left, top_right, bottom_right, bottom_left } = metadata.fiducials;

    // Acceptance Criteria: Top-left precisely 14x14 mm, others 8x8 mm
    if (!top_left || top_left.width_mm !== 14.0 || top_left.height_mm !== 14.0) {
      errors.push(`Top-left fiducial must be precisely 14x14 mm (got ${top_left?.width_mm}x${top_left?.height_mm})`);
    }

    [top_right, bottom_right, bottom_left].forEach((f, idx) => {
      const name = ['top_right', 'bottom_right', 'bottom_left'][idx];
      if (!f || f.width_mm !== 8.0 || f.height_mm !== 8.0) {
        errors.push(`${name} fiducial must be precisely 8x8 mm (got ${f?.width_mm}x${f?.height_mm})`);
      }
    });

    // Timing tracks validation
    const { left_bars, right_bars } = metadata.timing_tracks || {};
    if (!Array.isArray(left_bars) || left_bars.length === 0) {
      errors.push('Missing left timing tracks');
    } else {
      const invalidLeft = left_bars.find(b => b.width_mm !== 5.0 || b.height_mm !== 1.6);
      if (invalidLeft) {
        errors.push(`Left timing bar must be 5.0x1.6 mm (found row ${invalidLeft.row_index})`);
      }
    }

    if (!Array.isArray(right_bars) || right_bars.length === 0) {
      errors.push('Missing right timing tracks');
    } else {
      const invalidRight = right_bars.find(b => b.width_mm !== 3.0 || b.height_mm !== 1.6);
      if (invalidRight) {
        errors.push(`Right timing bar must be 3.0x1.6 mm (found row ${invalidRight.row_index})`);
      }
    }

    // Questions validation
    if (!Array.isArray(metadata.questions) || metadata.questions.length === 0) {
      errors.push('No questions defined in metadata');
    }

    return {
      valid: errors.length === 0,
      errors
    };
  }
}

// Export for Node.js testing and Browser window
if (typeof module !== 'undefined' && module.exports) {
  module.exports = OmrPdfGenerator;
}

