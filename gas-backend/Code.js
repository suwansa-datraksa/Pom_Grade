/**
 * PRODUCTION-GRADE HYBRID OMR EXAM SYSTEM — CLOUD BACKEND
 * Architecture Specification v6 (docs/architecture.md Section 6)
 *
 * Responsibilities:
 *  - Secure high-throughput scan ingestion via doPost (OAuth Bearer authenticated)
 *  - Append-only Raw_Scans ledger persistence
 *  - High-performance bounded window deduplication (last 5,000 rows)
 *  - Auto-provisioning of Index overview and dynamic Room_XXX tabs with dynamic formulas
 *
 * Credit: จัดทำโดย ครูสุวรรณษา เดชรักษา
 */

var CONFIG = {
  SPREADSHEET_ID: '1KcL20IBw5r36ODy9lcsBal6SmeZ9rRitctotZfwJKH4',
  BOUNDED_WINDOW_ROWS: 5000,
  LOCK_TIMEOUT_MS: 15000,
  CREDIT_FOOTER: 'จัดทำโดย ครูสุวรรณษา เดชรักษา',
  SHEETS: {
    KEYS: 'Protected_Keys',
    EXAMS: 'Exams',
    STUDENTS: 'Students',
    RAW_SCANS: 'Raw_Scans',
    INDEX: 'Index',
    ROOM_PREFIX: 'Room_'
  },
  THEME: {
    PRIMARY: '#1E3A8A',
    PRIMARY_LIGHT: '#2563EB',
    HEADER_TEXT: '#FFFFFF',
    ROW_BG: '#FFFFFF',
    ROW_ALT_BG: '#F8FAFC',
    BORDER: '#CBD5E1',
    STATUS_PASS: '#166534',
    STATUS_FAIL: '#991B1B'
  }
};

const SHEET_KEYS = CONFIG.SHEETS.KEYS;
const SHEET_EXAMS = CONFIG.SHEETS.EXAMS;
const SHEET_STUDENTS = CONFIG.SHEETS.STUDENTS;
const SHEET_SCANS = CONFIG.SHEETS.RAW_SCANS;
const SHEET_INDEX = CONFIG.SHEETS.INDEX;

const MAX_RECORDS = 200;
const MAX_BODY_CHARS = 250000;
const SCAN_HEADERS = [
  'ScanID', 'ScannedAt', 'ReceivedAt', 'DeviceID', 'ExamID',
  'StudentCode', 'Score', 'MaxScore', 'Answers', 'Status'
];
const TEXT_COLS = [1, 2, 3, 4, 5, 6, 9, 10]; // Columns requiring Plain text format

const RE_ID = /^[A-Za-z0-9._-]{8,64}$/;
const RE_EXAM = /^[A-Za-z0-9._-]{1,40}$/;
const RE_STUDENT = /^[0-9]{5}$/;
const RE_ANS = /^[0-4\-*?A-Za-z]+$/;

// =============================================================================
// HTTP HANDLERS (doGet / doPost)
// =============================================================================

function doGet(e) {
  try {
    const action = (e && e.parameter && e.parameter.action) ? e.parameter.action.toLowerCase() : 'health';
    const ss = getSpreadsheet_();

    if (action === 'provision') {
      const studentsMap = loadStudentsMap_(ss);
      const provResult = provisionMultiRoomViews_(ss, studentsMap);
      return json_({
        status: 'SUCCESS',
        action: 'provision',
        result: provResult,
        credit: CONFIG.CREDIT_FOOTER
      });
    }

    if (action === 'init') {
      const seeded = seedInitialDataIfEmpty_(ss);
      const studentsMap = loadStudentsMap_(ss);
      const provResult = provisionMultiRoomViews_(ss, studentsMap);
      return json_({
        status: 'SUCCESS',
        action: 'init',
        seeded: seeded,
        result: provResult,
        credit: CONFIG.CREDIT_FOOTER
      });
    }

    return json_({
      status: 'OK',
      version: 'v6-multi-room',
      service: 'Hybrid OMR Ingestion Backend',
      spreadsheet_id: ss.getId(),
      bounded_window_limit: CONFIG.BOUNDED_WINDOW_ROWS,
      credit: CONFIG.CREDIT_FOOTER,
      timestamp: new Date().toISOString()
    });

  } catch (err) {
    return json_({ status: 'ERROR', message: err.toString(), credit: CONFIG.CREDIT_FOOTER });
  }
}

function doPost(e) {
  const lock = LockService.getScriptLock();
  if (!lock.tryLock(CONFIG.LOCK_TIMEOUT_MS)) {
    return json_({ status: 'BUSY', success: false, error: 'ERR_CONCURRENT_LOCK', message: 'Server busy' });
  }

  try {
    if (!e || !e.postData || !e.postData.contents) {
      return json_({ status: 'BAD_REQUEST', success: false, error: 'ERR_EMPTY_PAYLOAD', message: 'empty body' });
    }

    const raw = e.postData.contents;
    if (raw.length > MAX_BODY_CHARS) {
      return json_({ status: 'BAD_REQUEST', success: false, error: 'ERR_PAYLOAD_TOO_LARGE', message: 'body too large' });
    }

    let payload;
    try {
      payload = JSON.parse(raw);
    } catch (parseErr) {
      return json_({ status: 'BAD_REQUEST', success: false, error: 'ERR_INVALID_JSON', message: 'invalid json' });
    }

    const ss = getSpreadsheet_();

    // Administrative provision request via POST
    if (payload.action === 'provision') {
      const studentsMap = loadStudentsMap_(ss);
      const provResult = provisionMultiRoomViews_(ss, studentsMap);
      return json_({ status: 'SUCCESS', success: true, result: provResult, credit: CONFIG.CREDIT_FOOTER });
    }

    // Check exam_id requirement
    const batchExamId = payload.exam_id ? String(payload.exam_id).trim() : '';

    // Normalize records collection: accepts either payload.records or payload.scans
    let records = [];
    if (Array.isArray(payload.records)) {
      if (payload.records.length === 0) {
        return json_({ status: 'BAD_REQUEST', success: false, error: 'ERR_BAD_FORMAT', message: 'records array cannot be empty' });
      }
      records = payload.records;
    } else if (Array.isArray(payload.scans)) {
      if (payload.scans.length === 0) {
        return json_({ status: 'BAD_REQUEST', success: false, error: 'ERR_BAD_FORMAT', message: 'scans array cannot be empty' });
      }
      if (!batchExamId && (!payload.scans[0] || !payload.scans[0].exam_id)) {
        return json_({ status: 'BAD_REQUEST', success: false, error: 'ERR_BAD_FORMAT', message: 'Missing exam_id in payload' });
      }

      const batchDeviceId = payload.device_id || 'unknown';
      records = payload.scans.map(s => ({
        scan_id: s.scan_id,
        exam_id: s.exam_id || batchExamId,
        student_code: s.student_id || s.student_code,
        device_id: s.device_id || batchDeviceId,
        score: s.score,
        total_questions: s.total_questions,
        ans: Array.isArray(s.answers) ? s.answers.join('') : (s.answers || s.ans || ''),
        scanned_at: s.scanned_at ? (typeof s.scanned_at === 'number' ? s.scanned_at : new Date(s.scanned_at).getTime()) : Date.now()
      }));
    } else {
      return json_({
        status: 'BAD_REQUEST',
        success: false,
        error: 'ERR_BAD_FORMAT',
        message: 'invalid records or scans collection'
      });
    }

    if (records.length > MAX_RECORDS) {
      return json_({ status: 'BAD_REQUEST', success: false, error: 'ERR_MAX_RECORDS_EXCEEDED', message: 'too many records' });
    }

    const keys = loadKeys_(ss);
    const studentsMap = loadStudentsMap_(ss);
    const scansSheet = getScansSheet_(ss);
    const seen = loadScanIds_(scansSheet);
    const receivedAt = new Date().toISOString();

    const rows = [];
    const results = [];
    let insertedCount = 0;
    let duplicateCount = 0;
    const insertedIds = [];

    for (let i = 0; i < records.length; i++) {
      const r = records[i];
      const id = (r && typeof r.scan_id === 'string') ? r.scan_id.trim() : '';

      if (!RE_ID.test(id)) {
        results.push({ scan_id: id, result: 'REJECTED', reason: 'BAD_FORMAT' });
        continue;
      }

      const isDup = (seen instanceof Set) ? seen.has(id) : !!seen[id];
      if (isDup) {
        duplicateCount++;
        results.push({ scan_id: id, result: 'DUPLICATE' });
        continue;
      }

      const examId = String(r.exam_id || '').trim();
      const student = String(r.student_code || r.student_id || '').trim();
      const deviceId = String(r.device_id || 'device').trim();
      const ans = String(r.ans || '').trim();
      const scannedAtNum = Number(r.scanned_at) || Date.now();

      if (!RE_EXAM.test(examId) || !RE_STUDENT.test(student) || !RE_ID.test(deviceId) || !isFinite(scannedAtNum)) {
        results.push({ scan_id: id, result: 'REJECTED', reason: 'BAD_FORMAT' });
        continue;
      }

      // Check exam registration
      const k = keys[examId];
      if (!k) {
        results.push({ scan_id: id, result: 'REJECTED', reason: 'UNKNOWN_EXAM' });
        if (records.length === 1) {
          return json_({
            success: false,
            error: 'ERR_UNKNOWN_EXAM',
            message: 'Exam ID "' + examId + '" is not registered in the Exams/Protected_Keys sheet.',
            results: results,
            credit: CONFIG.CREDIT_FOOTER
          });
        }
        continue;
      }

      let score = 0;
      let maxScore = k.totalQuestions || k.key.length;

      // Server-side grading or client score
      if (r.score !== undefined && r.total_questions !== undefined) {
        score = Number(r.score);
        maxScore = Number(r.total_questions);
      } else if (k.key && k.key.length > 0) {
        maxScore = 0;
        for (let q = 0; q < k.key.length; q++) {
          const w = (k.weights && k.weights[q] !== undefined) ? k.weights[q] : 1;
          maxScore += w;
          if (q < ans.length && ans.charAt(q).toUpperCase() === k.key.charAt(q).toUpperCase()) {
            score += w;
          }
        }
      }

      let flag = 'OK';
      if (ans.indexOf('?') !== -1) {
        flag = 'NEEDS_REVIEW';
      } else if (studentsMap.size > 0 && !studentsMap.has(student)) {
        flag = 'UNKNOWN_STUDENT';
      }

      if (seen instanceof Set) {
        seen.add(id);
      } else {
        seen[id] = true;
      }

      const scannedIso = new Date(scannedAtNum).toISOString();

      rows.push([
        id,
        scannedIso,
        receivedAt,
        deviceId,
        examId,
        student,
        score,
        maxScore,
        ans,
        flag
      ]);

      insertedCount++;
      insertedIds.push(id);
      results.push({
        scan_id: id,
        result: 'ACCEPTED',
        score: score,
        max_score: maxScore,
        flag: flag
      });
    }

    if (rows.length > 0) {
      const start = scansSheet.getLastRow() + 1;
      TEXT_COLS.forEach(function (c) {
        scansSheet.getRange(start, c, rows.length, 1).setNumberFormat('@');
      });
      scansSheet.getRange(start, 1, rows.length, SCAN_HEADERS.length).setValues(rows);
      SpreadsheetApp.flush();

      // Ensure multi-room views
      provisionMultiRoomViews_(ss, studentsMap);
    }

    return json_({
      status: 'SUCCESS',
      success: true,
      total_received: records.length,
      inserted: insertedCount,
      duplicates: duplicateCount,
      inserted_scan_ids: insertedIds,
      results: results,
      credit: CONFIG.CREDIT_FOOTER,
      synced_at: receivedAt
    });

  } catch (err) {
    console.error(err);
    return json_({ status: 'ERROR', success: false, error: 'ERR_SERVER_EXCEPTION', message: err.toString(), credit: CONFIG.CREDIT_FOOTER });
  } finally {
    try { lock.releaseLock(); } catch (e) {}
  }
}

// =============================================================================
// DATA ACCESS & BOUNDED WINDOW DEDUPLICATION
// =============================================================================

function json_(obj) {
  const output = ContentService.createTextOutput(JSON.stringify(obj));
  output.setMimeType(ContentService.MimeType.JSON);
  return output;
}

function getScansSheet_(ss) {
  let sh = ss.getSheetByName(SHEET_SCANS);
  if (!sh) {
    sh = ss.insertSheet(SHEET_SCANS);
    sh.appendRow(SCAN_HEADERS);
    TEXT_COLS.forEach(c => sh.getRange(1, c, 1, 1).setNumberFormat('@'));
  }
  return sh;
}

/**
 * Memory-optimized bounded window query (last 5,000 rows).
 */
function loadScanIds_(scans) {
  const ids = new Set();
  const last = scans.getLastRow();
  if (last < 2) return ids;

  const totalDataRows = last - 1;
  const numRows = Math.min(totalDataRows, CONFIG.BOUNDED_WINDOW_ROWS);
  const startRow = last - numRows + 1;

  const values = scans.getRange(startRow, 1, numRows, 1).getValues();
  for (let i = 0; i < values.length; i++) {
    const val = values[i][0];
    if (val !== '' && val !== null && val !== undefined) {
      ids[String(val)] = true;
      ids.add(String(val));
    }
  }
  return ids;
}

function loadKeys_(ss) {
  const map = {};

  // Check Protected_Keys
  let sh = ss.getSheetByName(SHEET_KEYS) || ss.getSheetByName(SHEET_EXAMS);
  if (sh && sh.getLastRow() >= 2) {
    const data = sh.getDataRange().getValues();
    for (let i = 1; i < data.length; i++) {
      const examId = String(data[i][0]).trim();
      const key = String(data[i][1]).trim();
      if (!examId) continue;
      const wRaw = String(data[i][2] || '').trim();
      let weights = key.split('').map(() => 1);
      if (wRaw) {
        const parsed = wRaw.split(',').map(Number);
        if (parsed.length === key.length && parsed.every(x => isFinite(x) && x >= 0)) {
          weights = parsed;
        }
      }
      map[examId] = { key: key, weights: weights, totalQuestions: key.length };
    }
  }

  return map;
}

function loadStudentsMap_(ss) {
  const map = new Map();
  const sh = ss.getSheetByName(SHEET_STUDENTS);
  if (!sh || sh.getLastRow() < 2) return map;

  const data = sh.getDataRange().getValues();
  for (let i = 1; i < data.length; i++) {
    const code = String(data[i][0]).trim();
    if (code) {
      map.set(code, {
        name: String(data[i][1]).trim(),
        classroom: String(data[i][2]).trim(),
        seatNo: data[i][3]
      });
    }
  }
  return map;
}

// =============================================================================
// MULTI-ROOM & INDEX PROVISIONING ENGINE
// =============================================================================

function provisionMultiRoomViews_(ss, studentsMap) {
  if (!studentsMap || studentsMap.size === 0) {
    studentsMap = loadStudentsMap_(ss);
  }

  const rooms = new Set();
  studentsMap.forEach(s => { if (s.classroom) rooms.add(s.classroom); });
  if (rooms.size === 0) return { status: 'NO_ROOMS' };

  const roomList = Array.from(rooms).sort();
  const indexSheet = ss.getSheetByName(SHEET_INDEX) || ss.insertSheet(SHEET_INDEX, 0);

  if (indexSheet.getLastRow() === 0) {
    indexSheet.appendRow(["ห้องเรียน", "ลิงก์เข้าดูข้อมูล", "จำนวนนักเรียน", "ส่งแล้ว", "ยังไม่พบผลสแกน", "คะแนนเฉลี่ย"]);
    indexSheet.getRange("A1:F1").setFontWeight("bold").setBackground("#E5E7EB");
    indexSheet.setFrozenRows(1);
  }

  const provisionedRooms = [];

  roomList.forEach((room, idx) => {
    const tabName = "Room_" + room.replace(/[\/\\?*:[\]]/g, '-');
    let rSheet = ss.getSheetByName(tabName);

    if (!rSheet) {
      rSheet = ss.insertSheet(tabName);
      rSheet.getRange("A1").setValue("ชุดข้อสอบ (ExamID):");
      rSheet.getRange("B1").setValue("EXAM_001").setNumberFormat('@').setFontWeight("bold");

      const headers = ["เลขที่", "รหัสนักเรียน", "ชื่อ - นามสกุล", "คำตอบที่อ่านได้", "คะแนน", "เต็ม", "เวลาสแกน", "สถานะ"];
      rSheet.getRange("A2:H2").setValues([headers]).setFontWeight("bold").setBackground("#F3F4F6");

      rSheet.getRange("A3").setFormula(`=IFERROR(FILTER(Students!D2:D, Students!C2:C = "${room}"), "")`);
      rSheet.getRange("B3").setFormula(`=IFERROR(FILTER(Students!A2:A, Students!C2:C = "${room}"), "")`);
      rSheet.getRange("C3").setFormula(`=IFERROR(FILTER(Students!B2:B, Students!C2:C = "${room}"), "")`);

      rSheet.getRange("D3").setFormula(
        `=MAP(B3:INDEX(B3:B, COUNTA(B3:B)), LAMBDA(sid, ` +
        `IF(sid="", {"", "", "", "", ""}, ` +
        `IFERROR(CHOOSEROWS(FILTER(CHOOSECOLS(Raw_Scans!A$2:J, 9, 7, 8, 2, 10), Raw_Scans!E$2:E = $B$1, Raw_Scans!F$2:F = sid), -1), ` +
        `{"-", "-", "-", "-", "⚠️ ยังไม่พบผลสแกน"}))))`
      );

      rSheet.setFrozenRows(2);
    }

    const rowIdx = idx + 2;
    indexSheet.getRange(rowIdx, 1).setValue(room);
    indexSheet.getRange(rowIdx, 2).setFormula(`=HYPERLINK("#gid=${rSheet.getSheetId()}", "เปิดดูห้อง ${room}")`);
    indexSheet.getRange(rowIdx, 3).setFormula(`=COUNTA('${tabName}'!A3:A)`);
    indexSheet.getRange(rowIdx, 4).setFormula(`=COUNTIF('${tabName}'!E3:E, ">=0")`);
    indexSheet.getRange(rowIdx, 5).setFormula(`=COUNTIF('${tabName}'!H3:H, "*ยังไม่พบผลสแกน*")`);
    indexSheet.getRange(rowIdx, 6).setFormula(`=IFERROR(AVERAGE('${tabName}'!E3:E), 0)`);

    provisionedRooms.push({ room_name: room, tab_name: tabName, sheet_id: rSheet.getSheetId() });
  });

  return {
    status: 'SUCCESS',
    rooms_provisioned: roomList.length,
    rooms: provisionedRooms,
    credit: CONFIG.CREDIT_FOOTER
  };
}

// =============================================================================
// UTILITIES & SAMPLE DATA SEEDING
// =============================================================================

function getSpreadsheet_() {
  try {
    const active = SpreadsheetApp.getActiveSpreadsheet();
    if (active) return active;
  } catch (e) {}
  return SpreadsheetApp.openById(CONFIG.SPREADSHEET_ID);
}

function seedInitialDataIfEmpty_(ss) {
  let seeded = false;

  // Ensure Protected_Keys / Exams
  let keysSh = ss.getSheetByName(SHEET_KEYS) || ss.getSheetByName(SHEET_EXAMS);
  if (!keysSh) {
    keysSh = ss.insertSheet(SHEET_KEYS);
  }
  if (keysSh.getLastRow() < 2) {
    if (keysSh.getLastRow() === 0) {
      keysSh.appendRow(['ExamID', 'Key', 'Weights']);
    }
    keysSh.appendRow(['EXAM_001', 'ABCDABCDABCDABCDABCD', '1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1']);
    keysSh.appendRow(['PHYS40-2026A', 'ABCDABCDABCDABCDABCD', '1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1']);
    seeded = true;
  }

  // Ensure Students
  let studSh = ss.getSheetByName(SHEET_STUDENTS);
  if (!studSh) {
    studSh = ss.insertSheet(SHEET_STUDENTS);
  }
  if (studSh.getLastRow() < 2) {
    if (studSh.getLastRow() === 0) {
      studSh.appendRow(['StudentID', 'Name', 'Classroom', 'SeatNo']);
    }
    studSh.appendRow(['10001', 'นายสมชาย ใจดี', 'ม.5/1', 1]);
    studSh.appendRow(['10002', 'นางสาวสมหญิง ศรีสยาม', 'ม.5/1', 2]);
    studSh.appendRow(['10003', 'นายณัฐวุฒิ สิทธิโชค', 'ม.5/1', 3]);
    studSh.appendRow(['10006', 'นายอานนท์ มั่นคง', 'ม.5/2', 1]);
    studSh.appendRow(['10007', 'นางสาวจารุวรรณ แก้วใส', 'ม.5/2', 2]);
    seeded = true;
  }

  // Ensure Raw_Scans
  getScansSheet_(ss);

  return seeded;
}

// =============================================================================
// GAS EDITOR EXECUTABLE UNIT TEST RUNNER
// =============================================================================

function runGasUnitTests() {
  Logger.log('====================================================');
  Logger.log('STARTING GAS BACKEND UNIT TESTS (v6)');
  Logger.log('Credit: ' + CONFIG.CREDIT_FOOTER);
  Logger.log('====================================================');

  const ss = getSpreadsheet_();
  seedInitialDataIfEmpty_(ss);

  // Test 1: Unknown Exam Rejection
  Logger.log('\n[Test 1] Testing rejection of unknown exam...');
  const res1 = JSON.parse(doPost({
    postData: {
      contents: JSON.stringify({
        records: [{
          scan_id: 'test_uuid_0000',
          exam_id: 'EXAM_UNKNOWN_9999',
          student_code: '10001',
          device_id: 'unit_tester',
          ans: 'ABCD',
          scanned_at: Date.now()
        }]
      })
    }
  }).getContent());

  if (!res1.success && (res1.error === 'ERR_UNKNOWN_EXAM' || (res1.results && res1.results[0].reason === 'UNKNOWN_EXAM'))) {
    Logger.log(' PASSED: Unknown exam correctly rejected.');
  } else {
    throw new Error('FAILED Test 1: Expected rejection but got ' + JSON.stringify(res1));
  }

  // Test 2: Valid Batch Insertion
  Logger.log('\n[Test 2] Testing valid batch insertion...');
  const testId1 = 'test_uuid_' + Utilities.getUuid().substring(0, 18);
  const testId2 = 'test_uuid_' + Utilities.getUuid().substring(0, 18);
  const res2 = JSON.parse(doPost({
    postData: {
      contents: JSON.stringify({
        records: [
          { scan_id: testId1, exam_id: 'EXAM_001', student_code: '10001', device_id: 'unit_tester', ans: 'ABCDABCDABCDABCDABCD', scanned_at: Date.now() },
          { scan_id: testId2, exam_id: 'EXAM_001', student_code: '10002', device_id: 'unit_tester', ans: 'ABCDABCDABCDABCDABCD', scanned_at: Date.now() }
        ]
      })
    }
  }).getContent());

  if (res2.success && res2.inserted === 2) {
    Logger.log(' PASSED: Batch of 2 scans successfully inserted.');
  } else {
    throw new Error('FAILED Test 2: ' + JSON.stringify(res2));
  }

  // Test 3: Deduplication via scan_id
  Logger.log('\n[Test 3] Testing deduplication of existing scan_id...');
  const res3 = JSON.parse(doPost({
    postData: {
      contents: JSON.stringify({
        records: [
          { scan_id: testId1, exam_id: 'EXAM_001', student_code: '10001', device_id: 'unit_tester', ans: 'ABCDABCDABCDABCDABCD', scanned_at: Date.now() },
          { scan_id: 'test_uuid_' + Utilities.getUuid().substring(0, 18), exam_id: 'EXAM_001', student_code: '10003', device_id: 'unit_tester', ans: 'ABCDABCDABCDABCDABCD', scanned_at: Date.now() }
        ]
      })
    }
  }).getContent());

  if (res3.success && res3.inserted === 1 && res3.duplicates === 1) {
    Logger.log(' PASSED: 1 duplicate suppressed, 1 new record inserted.');
  } else {
    throw new Error('FAILED Test 3: ' + JSON.stringify(res3));
  }

  Logger.log('\n====================================================');
  Logger.log('ALL UNIT TESTS COMPLETED SUCCESSFULLY!');
  Logger.log('Credit: ' + CONFIG.CREDIT_FOOTER);
  Logger.log('====================================================');

  return { success: true };
}
