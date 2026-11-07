/**
 * PRODUCTION-GRADE HYBRID OMR EXAM SYSTEM — TEMPLATE GENERATOR CONFIG
 * Architecture Specification v6 (Phase 2)
 *
 * Credit: จัดทำโดย ครูสุวรรณษา เดชรักษา
 */

const TEMPLATE_CONFIG = {
  CREDIT: 'จัดทำโดย ครูสุวรรณษา เดชรักษา',
  PAGE: {
    FORMAT: 'a4',
    ORIENTATION: 'portrait',
    WIDTH_MM: 210.0,
    HEIGHT_MM: 297.0
  },
  FIDUCIALS: {
    // Margins from paper edge to outer edge of fiducials
    MARGIN_X_MM: 12.0,
    MARGIN_Y_MM: 12.0,
    // Asymmetric Top-Left corner marker for rotation detection (90/180/270 deg)
    TOP_LEFT: { WIDTH_MM: 14.0, HEIGHT_MM: 14.0 },
    // Other 3 corner markers
    STANDARD: { WIDTH_MM: 8.0, HEIGHT_MM: 8.0 }
  },
  TIMING_TRACKS: {
    // Asymmetric Dual-Sided Timing Bars
    LEFT_BAR: { WIDTH_MM: 5.0, HEIGHT_MM: 1.6 },
    RIGHT_BAR: { WIDTH_MM: 3.0, HEIGHT_MM: 1.6 },
    MARGIN_LEFT_X_MM: 16.0,
    MARGIN_RIGHT_X_MM: 194.0 - 3.0
  },
  STUDENT_ID: {
    DIGITS: 5,
    START_X_MM: 32.0,
    START_Y_MM: 38.0,
    COL_SPACING_MM: 7.2,
    ROW_SPACING_MM: 5.4,
    BUBBLE_RADIUS_MM: 2.1
  },
  BUBBLES: {
    RADIUS_MM: 2.3,
    BORDER_COLOR: '#666666',
    TEXT_COLOR: '#555555',
    LINE_WIDTH_MM: 0.25
  },
  PRESETS: {
    '20_QUESTIONS_4_CHOICES': {
      questions: 20,
      choices: ['A', 'B', 'C', 'D'],
      columns: 1,
      startY: 110.0,
      rowHeight: 7.8,
      choiceSpacing: 9.0
    },
    '40_QUESTIONS_4_CHOICES': {
      questions: 40,
      choices: ['A', 'B', 'C', 'D'],
      columns: 2,
      startY: 105.0,
      rowHeight: 7.8,
      choiceSpacing: 8.5
    },
    '60_QUESTIONS_4_CHOICES': {
      questions: 60,
      choices: ['A', 'B', 'C', 'D'],
      columns: 2,
      startY: 102.0,
      rowHeight: 5.5,
      choiceSpacing: 7.5
    },
    '40_QUESTIONS_5_CHOICES': {
      questions: 40,
      choices: ['A', 'B', 'C', 'D', 'E'],
      columns: 2,
      startY: 105.0,
      rowHeight: 7.8,
      choiceSpacing: 7.5
    }
  }
};

// Export for Node.js testing and Browser window
if (typeof module !== 'undefined' && module.exports) {
  module.exports = TEMPLATE_CONFIG;
}

