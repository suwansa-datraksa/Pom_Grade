/**
 * Google Sheets API v4 Client Module
 * Reads Raw_Scans, Students, and Protected_Keys with zero middle-tier storage.
 * Performs in-memory deduplication and latest-scan resolution per (ExamID, StudentCode).
 *
 * Pom Grade - จัดทำโดย ครูสุวรรณษา เดชรักษา
 */

(function (window) {
  'use strict';

  const SHEETS_API_BASE = 'https://sheets.googleapis.com/v4/spreadsheets';

  const SheetsClient = {
    /**
     * Batch fetch required sheets: Raw_Scans, Students, Protected_Keys
     * @param {string} spreadsheetId
     * @param {string} accessToken
     * @returns {Promise<Object>}
     */
    async fetchAllData(spreadsheetId, accessToken) {
      if (!spreadsheetId || !accessToken) {
        throw new Error('Spreadsheet ID and access token are required');
      }

      const ranges = [
        'Raw_Scans!A1:J',
        'Students!A1:D',
        'Protected_Keys!A1:D'
      ];
      const rangesParam = ranges.map(r => `ranges=${encodeURIComponent(r)}`).join('&');
      const url = `${SHEETS_API_BASE}/${spreadsheetId}/values:batchGet?${rangesParam}&valueRenderOption=UNFORMATTED_VALUE`;

      const response = await fetch(url, {
        headers: {
          'Authorization': `Bearer ${accessToken}`,
          'Accept': 'application/json'
        }
      });

      if (!response.ok) {
        const errorData = await response.json().catch(() => ({}));
        const msg = errorData.error ? errorData.error.message : `HTTP ${response.status} ${response.statusText}`;
        throw new Error(`Sheets API Error: ${msg}`);
      }

      const data = await response.json();
      const valueRanges = data.valueRanges || [];

      const rawScansValues = (valueRanges[0] && valueRanges[0].values) ? valueRanges[0].values : [];
      const studentsValues = (valueRanges[1] && valueRanges[1].values) ? valueRanges[1].values : [];
      const keysValues = (valueRanges[2] && valueRanges[2].values) ? valueRanges[2].values : [];

      const rawScans = this.parseRawScans(rawScansValues);
      const students = this.parseStudents(studentsValues);
      const answerKeys = this.parseProtectedKeys(keysValues);

      // Resolve latest scans per (ExamID, StudentCode)
      const latestScansMap = this.groupLatestScans(rawScans);

      return {
        rawScans,
        students,
        answerKeys,
        latestScansMap,
        fetchedAt: new Date().toISOString()
      };
    },

    /**
     * Parse Raw_Scans 2D array
     * Columns: ScanID(0), ScannedAt(1), ReceivedAt(2), DeviceID(3), ExamID(4),
     *          StudentCode(5), Score(6), MaxScore(7), Answers(8), Status(9)
     */
    parseRawScans(rows) {
      if (!rows || rows.length <= 1) return [];

      const headers = rows[0].map(h => String(h || '').trim());
      const scans = [];

      for (let i = 1; i < rows.length; i++) {
        const row = rows[i];
        if (!row || row.length === 0 || !row[0]) continue;

        scans.push({
          scanId: String(row[0] || '').trim(),
          scannedAt: String(row[1] || '').trim(),
          receivedAt: String(row[2] || '').trim(),
          deviceId: String(row[3] || '').trim(),
          examId: String(row[4] || '').trim(),
          studentCode: String(row[5] || '').trim().padStart(5, '0'),
          score: parseFloat(row[6]) || 0,
          maxScore: parseFloat(row[7]) || 0,
          answers: String(row[8] || '').trim(),
          status: String(row[9] || '').trim()
        });
      }
      return scans;
    },

    /**
     * Parse Students 2D array
     * Columns: StudentCode(0), Name(1), Classroom(2), SeatNo(3)
     */
    parseStudents(rows) {
      if (!rows || rows.length <= 1) return [];

      const students = [];
      for (let i = 1; i < rows.length; i++) {
        const row = rows[i];
        if (!row || row.length === 0 || !row[0]) continue;

        students.push({
          studentCode: String(row[0] || '').trim().padStart(5, '0'),
          name: String(row[1] || '').trim(),
          classroom: String(row[2] || '').trim(),
          seatNo: parseInt(row[3], 10) || i
        });
      }
      return students;
    },

    /**
     * Parse Protected_Keys 2D array
     * Columns: ExamID(0), AnswerKey(1), Weights(2), TotalQ(3)
     */
    parseProtectedKeys(rows) {
      if (!rows || rows.length <= 1) return {};

      const keys = {};
      for (let i = 1; i < rows.length; i++) {
        const row = rows[i];
        if (!row || row.length === 0 || !row[0]) continue;

        const examId = String(row[0] || '').trim();
        keys[examId] = {
          examId: examId,
          answerKey: String(row[1] || '').trim().toUpperCase(),
          weights: String(row[2] || '').trim(),
          totalQ: parseInt(row[3], 10) || (row[1] ? String(row[1]).trim().length : 0)
        };
      }
      return keys;
    },

    /**
     * Group scans by (ExamID, StudentCode) and extract latest record by ScannedAt (or ReceivedAt)
     * @param {Array<Object>} scans
     * @returns {Map<string, Object>} key: `${examId}_${studentCode}` -> scan object
     */
    groupLatestScans(scans) {
      const map = new Map();

      for (const scan of scans) {
        if (!scan.examId || !scan.studentCode) continue;

        const key = `${scan.examId}_${scan.studentCode}`;
        const existing = map.get(key);

        if (!existing) {
          map.set(key, scan);
        } else {
          // Compare timestamps (ISO 8601 strings compare cleanly with lexicographical or Date.parse)
          const timeExisting = Date.parse(existing.scannedAt || existing.receivedAt) || 0;
          const timeCurrent = Date.parse(scan.scannedAt || scan.receivedAt) || 0;

          if (timeCurrent >= timeExisting) {
            map.set(key, scan);
          }
        }
      }

      return map;
    }
  };

  window.PomGradeSheetsClient = SheetsClient;

  if (typeof module !== 'undefined' && module.exports) {
    module.exports = SheetsClient;
  }
})(typeof window !== 'undefined' ? window : global);
