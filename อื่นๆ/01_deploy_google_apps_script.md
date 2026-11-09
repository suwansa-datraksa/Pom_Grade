# คู่มือขั้นตอนที่ 1: การ Deploy Google Apps Script ลงบน Google Sheets

> **Pom Grade — ระบบตรวจข้อสอบ OMR ไฮบริด (Architecture Specification v6)**  
> **จัดทำโดย ครูสุวรรณษา เดชรักษา**

---

เอกสารนี้อธิบายขั้นตอนการนำซอร์สโค้ดจากโฟลเดอร์ `gas-backend/` ไปติดตั้งและเปิดใช้งาน (Deploy) บน Google Sheets ของคุณครูอย่างละเอียด เพื่อทำหน้าที่เป็น Cloud Backend ในการบันทึกผลคะแนน คัดกรองความถูกต้อง ป้องกันการส่งซ้ำ (Deduplication) และสร้างแท็บสรุปผลคะแนนแยกรายห้องอัตโนมัติ

---

## ข้อมูลจำเพาะและไฟล์ที่เกี่ยวข้อง
* **สเปรดชีตเป้าหมาย:** [Google Sheets (Pom Grade)](https://docs.google.com/spreadsheets/d/1KcL20IBw5r36ODy9lcsBal6SmeZ9rRitctotZfwJKH4/edit) (ID: `1KcL20IBw5r36ODy9lcsBal6SmeZ9rRitctotZfwJKH4`)
* **ไฟล์ต้นฉบับในโปรเจกต์:**
  * `gas-backend/Code.js` (ซอร์สโค้ดหลักของ Apps Script V8 Engine)
  * `gas-backend/appsscript.json` (ไฟล์ Manifest กำหนดสิทธิ์และโซนเวลา)

---

## ขั้นตอนการติดตั้งทีละขั้นตอน (Step-by-Step)

### ขั้นตอนที่ 1.1: เปิดตัวแก้ไข Apps Script ใน Google Sheets
1. เปิดเว็บเบราว์เซอร์และเข้าไปที่ Google Sheets ของคุณครู:  
   `https://docs.google.com/spreadsheets/d/1KcL20IBw5r36ODy9lcsBal6SmeZ9rRitctotZfwJKH4/edit`
2. ที่แถบเมนูด้านบน คลิก **ส่วนขยาย (Extensions)** $\to$ เลือก **Apps Script**
3. ระบบจะเปิดแท็บใหม่ขึ้นมาพร้อมหน้าต่างตัวแก้ไขโปรแกรม (Apps Script Code Editor)

---

### ขั้นตอนที่ 1.2: คัดลอกซอร์สโค้ด `Code.js`
1. ในหน้าตัวแก้ไข Apps Script จะมีไฟล์ชื่อ `Code.gs` เปิดอยู่
2. ลบโค้ดเดิมที่มีอยู่ทั้งหมดออก (เช่น `function myFunction() { ... }`)
3. เปิดไฟล์ `gas-backend/Code.js` ในโปรเจกต์ คัดลอกโค้ดทั้งหมด (หรือนำโค้ดด้านล่างนี้) ไปวางในตัวแก้ไข:

```javascript
/**
 * Pom Grade — Google Apps Script Backend (Architecture v6)
 * จัดทำโดย ครูสุวรรณษา เดชรักษา
 */
var CONFIG = {
  SHEET_KEYS: 'Protected_Keys',
  SHEET_STUDENTS: 'Students',
  SHEET_SCANS: 'Raw_Scans',
  SHEET_INDEX: 'Index',
  MAX_RECORDS: 200,
  MAX_BODY_CHARS: 250000,
  SCAN_HEADERS: ['ScanID', 'ScannedAt', 'ReceivedAt', 'DeviceID', 'ExamID',
                 'StudentCode', 'Score', 'MaxScore', 'Answers', 'Status'],
  TEXT_COLS: [1, 2, 3, 4, 5, 6, 9, 10], // Plain text columns
  RE_ID: /^[A-Za-z0-9._-]{8,64}$/,
  RE_EXAM: /^[A-Za-z0-9._-]{1,40}$/,
  RE_STUDENT: /^[0-9]{5}$/,
  RE_ANS: /^[0-4A-E\-*?]+$/,
  CREDIT_FOOTER: 'จัดทำโดย ครูสุวรรณษา เดชรักษา'
};

function doGet(e) {
  return jsonResponse_({
    status: 'OK',
    app: 'Pom Grade',
    version: 'v6-multi-room',
    credit: CONFIG.CREDIT_FOOTER,
    timestamp: new Date().toISOString()
  });
}

function doPost(e) {
  var lock = LockService.getScriptLock();
  if (!lock.tryLock(15000)) {
    return jsonResponse_({ status: 'BUSY', message: 'Server is processing another batch. Please retry.' });
  }

  try {
    if (!e || !e.postData || !e.postData.contents) {
      return jsonResponse_({ status: 'BAD_REQUEST', message: 'Missing request body' });
    }
    var raw = e.postData.contents;
    if (raw.length > CONFIG.MAX_BODY_CHARS) {
      return jsonResponse_({ status: 'BAD_REQUEST', message: 'Payload exceeds size limit' });
    }

    var payload;
    try {
      payload = JSON.parse(raw);
    } catch (parseErr) {
      return jsonResponse_({ status: 'BAD_REQUEST', message: 'Malformed JSON payload' });
    }

    if (!payload || !Array.isArray(payload.records) || payload.records.length === 0 || payload.records.length > CONFIG.MAX_RECORDS) {
      return jsonResponse_({ status: 'BAD_REQUEST', message: 'Invalid records array (must be 1-200 items)' });
    }

    var ss = SpreadsheetApp.getActiveSpreadsheet();
    var keys = loadKeys_(ss);
    var studentsMap = loadStudentsMap_(ss);
    var scansSheet = getScansSheet_(ss);
    var seenScanIds = loadScanIds_(scansSheet);
    var receivedAt = new Date().toISOString();

    var rowsToInsert = [];
    var results = [];

    payload.records.forEach(function (r) {
      var id = r && typeof r.scan_id === 'string' ? r.scan_id.trim() : '';
      if (!CONFIG.RE_ID.test(id)) {
        results.push({ scan_id: id, result: 'REJECTED', reason: 'BAD_FORMAT' });
        return;
      }
      if (seenScanIds[id]) {
        results.push({ scan_id: id, result: 'DUPLICATE' });
        return;
      }

      var examId = String(r.exam_id || '').trim();
      var student = String(r.student_code || '').trim();
      var deviceId = String(r.device_id || '').trim();
      var ans = String(r.ans || '').trim().toUpperCase();
      var scannedAtTime = r.scanned_at;

      if (!CONFIG.RE_EXAM.test(examId) || !CONFIG.RE_STUDENT.test(student) || !CONFIG.RE_ID.test(deviceId) ||
          !CONFIG.RE_ANS.test(ans) || !scannedAtTime) {
        results.push({ scan_id: id, result: 'REJECTED', reason: 'BAD_FORMAT' });
        return;
      }

      var k = keys[examId];
      if (!k) {
        results.push({ scan_id: id, result: 'REJECTED', reason: 'UNKNOWN_EXAM' });
        return;
      }

      if (ans.length !== k.key.length) {
        results.push({ scan_id: id, result: 'REJECTED', reason: 'BAD_LENGTH' });
        return;
      }

      var score = 0;
      var max = 0;
      for (var q = 0; q < k.key.length; q++) {
        var w = k.weights[q];
        max += w;
        if (ans.charAt(q) === k.key.charAt(q)) {
          score += w;
        }
      }

      var flag = 'OK';
      if (ans.indexOf('?') !== -1 || ans.indexOf('*') !== -1) {
        flag = 'NEEDS_REVIEW';
      } else if (studentsMap.size > 0 && !studentsMap.has(student)) {
        flag = 'UNKNOWN_STUDENT';
      }

      seenScanIds[id] = true;
      var scannedIso = (typeof scannedAtTime === 'number') ? new Date(scannedAtTime).toISOString() : String(scannedAtTime);

      rowsToInsert.push([
        id, scannedIso, receivedAt, deviceId, examId,
        student, score, max, ans, flag
      ]);

      results.push({
        scan_id: id,
        result: 'ACCEPTED',
        score: score,
        max_score: max,
        flag: flag
      });
    });

    if (rowsToInsert.length > 0) {
      var startRow = scansSheet.getLastRow() + 1;
      CONFIG.TEXT_COLS.forEach(function (colIdx) {
        scansSheet.getRange(startRow, colIdx, rowsToInsert.length, 1).setNumberFormat('@');
      });
      scansSheet.getRange(startRow, 1, rowsToInsert.length, CONFIG.SCAN_HEADERS.length).setValues(rowsToInsert);
      SpreadsheetApp.flush();

      // สร้างและอัปเดตแท็บแยกห้องเรียน (Room_XXX) และ Index สารบัญอัตโนมัติ
      provisionMultiRoomViews_(ss, studentsMap);
    }

    return jsonResponse_({
      status: 'SUCCESS',
      received_count: payload.records.length,
      inserted_count: rowsToInsert.length,
      results: results,
      credit: CONFIG.CREDIT_FOOTER
    });

  } catch (err) {
    console.error(err);
    return jsonResponse_({ status: 'ERROR', message: err.toString() });
  } finally {
    lock.releaseLock();
  }
}

function jsonResponse_(obj) {
  return ContentService.createTextOutput(JSON.stringify(obj))
    .setMimeType(ContentService.MimeType.JSON);
}

function getScansSheet_(ss) {
  var sh = ss.getSheetByName(CONFIG.SHEET_SCANS);
  if (!sh) {
    sh = ss.insertSheet(CONFIG.SHEET_SCANS);
    sh.appendRow(CONFIG.SCAN_HEADERS);
    sh.getRange(1, 1, 1, CONFIG.SCAN_HEADERS.length).setFontWeight('bold').setBackground('#E2E8F0');
    CONFIG.TEXT_COLS.forEach(function (c) {
      sh.getRange(1, c, 1, 1).setNumberFormat('@');
    });
    sh.setFrozenRows(1);
  }
  return sh;
}

function loadKeys_(ss) {
  var sh = ss.getSheetByName(CONFIG.SHEET_KEYS);
  var map = {};
  if (!sh) return map;
  var data = sh.getDataRange().getValues();
  for (var i = 1; i < data.length; i++) {
    var examId = String(data[i][0]).trim();
    var key = String(data[i][1]).trim().toUpperCase();
    if (!examId || !CONFIG.RE_ANS.test(key)) continue;

    var wRaw = String(data[i][2] || '').trim();
    var weights = key.split('').map(function () { return 1; });
    if (wRaw) {
      var parsed = wRaw.split(',').map(Number);
      if (parsed.length === key.length && parsed.every(function (x) { return isFinite(x) && x >= 0; })) {
        weights = parsed;
      }
    }
    map[examId] = { key: key, weights: weights };
  }
  return map;
}

function loadStudentsMap_(ss) {
  var map = new Map();
  var sh = ss.getSheetByName(CONFIG.SHEET_STUDENTS);
  if (!sh) return map;
  var data = sh.getDataRange().getValues();
  for (var i = 1; i < data.length; i++) {
    var code = String(data[i][0]).trim();
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

function loadScanIds_(scans) {
  var ids = {};
  var last = scans.getLastRow();
  if (last < 2) return ids;
  var startRow = Math.max(2, last - 5000);
  var numRows = last - startRow + 1;
  var values = scans.getRange(startRow, 1, numRows, 1).getValues();
  for (var i = 0; i < values.length; i++) {
    ids[String(values[i][0])] = true;
  }
  return ids;
}

function provisionMultiRoomViews_(ss, studentsMap) {
  var rooms = new Set();
  studentsMap.forEach(function (s) {
    if (s.classroom) rooms.add(s.classroom);
  });
  if (rooms.size === 0) return;

  var roomList = Array.from(rooms).sort();
  var indexSheet = ss.getSheetByName(CONFIG.SHEET_INDEX) || ss.insertSheet(CONFIG.SHEET_INDEX, 0);

  if (indexSheet.getLastRow() === 0) {
    indexSheet.appendRow(['ห้องเรียน', 'ลิงก์เข้าดูข้อมูล', 'จำนวนนักเรียน', 'ส่งแล้ว', 'ยังไม่พบผลสแกน', 'คะแนนเฉลี่ย']);
    indexSheet.getRange('A1:F1').setFontWeight('bold').setBackground('#E2E8F0');
    indexSheet.setFrozenRows(1);
  }

  roomList.forEach(function (room, idx) {
    var tabName = 'Room_' + room.replace(/[\/\\?*:[\]]/g, '-');
    var rSheet = ss.getSheetByName(tabName);

    if (!rSheet) {
      rSheet = ss.insertSheet(tabName);
      rSheet.getRange('A1').setValue('ชุดข้อสอบ (ExamID):');
      rSheet.getRange('B1').setValue('EXAM_001').setNumberFormat('@').setFontWeight('bold');

      var headers = ['เลขที่', 'รหัสนักเรียน', 'ชื่อ - นามสกุล', 'คำตอบที่อ่านได้', 'คะแนน', 'เต็ม', 'เวลาสแกน', 'สถานะ'];
      rSheet.getRange('A2:H2').setValues([headers]).setFontWeight('bold').setBackground('#F1F5F9');

      rSheet.getRange('A3').setFormula('=IFERROR(FILTER(Students!D2:D, Students!C2:C = "' + room + '"), "")');
      rSheet.getRange('B3').setFormula('=IFERROR(FILTER(Students!A2:A, Students!C2:C = "' + room + '"), "")');
      rSheet.getRange('C3').setFormula('=IFERROR(FILTER(Students!B2:B, Students!C2:C = "' + room + '"), "")');

      rSheet.getRange('D3').setFormula(
        '=MAP(B3:INDEX(B3:B, COUNTA(B3:B)), LAMBDA(sid, ' +
        'IF(sid="", {"", "", "", "", ""}, ' +
        'IFERROR(CHOOSEROWS(FILTER(CHOOSECOLS(Raw_Scans!A$2:J, 9, 7, 8, 2, 10), Raw_Scans!E$2:E = $B$1, Raw_Scans!F$2:F = sid), -1), ' +
        '{"-", "-", "-", "-", "⚠️ ยังไม่พบผลสแกน"}))))'
      );

      rSheet.setFrozenRows(2);
    }

    var rowIdx = idx + 2;
    indexSheet.getRange(rowIdx, 1).setValue(room);
    indexSheet.getRange(rowIdx, 2).setFormula('=HYPERLINK("#gid=' + rSheet.getSheetId() + '", "เปิดดูห้อง ' + room + '")');
    indexSheet.getRange(rowIdx, 3).setFormula('=COUNTA(\'' + tabName + '\'!A3:A)');
    indexSheet.getRange(rowIdx, 4).setFormula('=COUNTIF(\'' + tabName + '\'!E3:E, ">=0")');
    indexSheet.getRange(rowIdx, 5).setFormula('=COUNTIF(\'' + tabName + '\'!H3:H, "*ยังไม่พบผลสแกน*")');
    indexSheet.getRange(rowIdx, 6).setFormula('=IFERROR(AVERAGE(\'' + tabName + '\'!E3:E), 0)');
  });
}
```

4. กดไอคอนแผ่นดิสก์ **บันทึกโปรเจกต์ (Save project)** หรือกด `Ctrl + S`

---

### ขั้นตอนที่ 1.3: กำหนดค่าไฟล์ Manifest `appsscript.json`
1. ที่แถบเมนูด้านซ้ายของ Apps Script ให้คลิกรูปเฟือง **การตั้งค่าโปรเจกต์ (Project Settings)**
2. ติ๊กเครื่องหมายถูกที่ช่อง:
   ☑ **แสดงไฟล์ Manifest "appsscript.json" ในตัวแก้ไข (Show "appsscript.json" manifest file in editor)**
3. คลิกกลับมาที่รูปโฟลเดอร์ **ตัวแก้ไข (Editor)** จะเห็นไฟล์ `appsscript.json` ปรากฏขึ้น
4. เปิดไฟล์ `appsscript.json` แล้วแทนที่ด้วยเนื้อหาต่อไปนี้:

```json
{
  "timeZone": "Asia/Bangkok",
  "dependencies": {},
  "exceptionLogging": "STACKDRIVER",
  "runtimeVersion": "V8",
  "webapp": {
    "executeAs": "USER_DEPLOYING",
    "access": "MYSELF"
  }
}
```

> ⚠️ **จุดสำคัญด้านความปลอดภัย (Zero-Secret Principle):**  
> * `executeAs: USER_DEPLOYING` = สคริปต์จะทำงานด้วยสิทธิ์ของบัญชีครูผู้ Deploy  
> * `access: MYSELF` = **เฉพาะบัญชีของครูเท่านั้นที่มีสิทธิ์เรียกใช้ผ่าน API** ป้องกันคนภายนอกยิงข้อมูลแปลกปลอมเข้ามา

5. กด **บันทึก (Ctrl + S)**

---

### ขั้นตอนที่ 1.4: สั่ง Deploy เว็บแอป (New Deployment)
1. ที่มุมขวาบนของหน้าจอ คลิกปุ่มสีน้ำเงิน **การทำให้ใช้งานได้ (Deploy)** $\to$ เลือก **การทำให้ใช้งานได้ใหม่ (New deployment)**
2. หน้าต่างการตั้งค่าจะปรากฏขึ้น:
   * คลิกรูปเฟืองด้านซ้าย **เลือกประเภท (Select type)** $\to$ เลือก **เว็บแอป (Web app)**
   * **คำอธิบาย (Description):** พิมพ์ `Pom Grade Backend v6`
   * **ดำเนินการในฐานะ (Execute as):** เลือก **ฉัน (Me / your-email@...)**
   * **ผู้มีสิทธิ์เข้าถึง (Who has access):** เลือก **เฉพาะฉัน (Only myself)**
3. คลิกปุ่ม **ทำให้ใช้งานได้ (Deploy)**
4. *(เฉพาะครั้งแรก)* Google จะขออนุญาตเข้าถึงสเปรดชีต:
   * คลิก **ตรวจสอบสิทธิ์ (Authorize access)**
   * เลือกบัญชี Google ของคุณครู
   * หากมีหน้าต่างแจ้งเตือน *"Google ยังไม่ได้ยืนยันแอปนี้"* ให้คลิกที่ **ขั้นสูง (Advanced)** $\to$ คลิก **ไปที่ Pom Grade (ไม่ปลอดภัย) / Go to Pom Grade (unsafe)**
   * คลิก **อนุญาต (Allow)**
5. เมื่อเสร็จสิ้น หน้าจอจะแสดงลิงก์ **URL ของเว็บแอป (Web App URL)** เช่น:
   ```text
   https://script.google.com/macros/s/AKfycbzXXXXXXXXXXXXXXXXXXXXX/exec
   ```
6. **ให้คลิก "คัดลอก (Copy)" URL นี้เก็บไว้** (เราจะต้องนำ URL นี้ไปใส่ในการตั้งค่าของแอปมือถือ Pom Grade)

---

### ขั้นตอนที่ 1.5: ทดสอบการทำงาน (Verification Test)
เปิด PowerShell หรือ Terminal แล้วทดสอบยิงคำสั่ง `curl` จำลอง:

#### ทดสอบที่ 1: ตรวจสอบสถานะ Server (GET Request)
```powershell
curl -L "https://script.google.com/macros/s/AKfycbzXXXXXXXXXXXXXXXXXXXXX/exec"
```
**ผลลัพธ์ที่ถูกต้อง:**
```json
{"status":"OK","app":"Pom Grade","version":"v6-multi-room","credit":"จัดทำโดย ครูสุวรรณษา เดชรักษา",...}
```

#### ทดสอบที่ 2: จำลองส่งผลสแกนพร้อม Bearer Token (POST Request)
```powershell
curl --location --post302 \
  -H "Authorization: Bearer <YOUR_ACCESS_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"records":[{"scan_id":"uuid-test-001","exam_id":"EXAM_001","student_code":"10001","device_id":"TEST_DEVICE","ans":"AAAAABBBBBCCCCCDDDDD","scanned_at":1762500000000}]}' \
  "https://script.google.com/macros/s/AKfycbzXXXXXXXXXXXXXXXXXXXXX/exec"
```

---

> **เสร็จสิ้นขั้นตอนที่ 1** ตอนนี้ Cloud Backend ของระบบ Pom Grade พร้อมรับข้อมูลแล้วครับ!

