# Cloud Backend & Sheet Provisioning (`gas-backend`)

> **ระบบคลาวด์แบ็กเอนด์ตรวจข้อสอบ OMR ไฮบริด (Architecture v6)**  
> **จัดทำโดย ครูสุวรรณษา เดชรักษา**

---

## 1. ภาพรวมระบบ (System Overview)
ส่วนประกอบคลาวด์แบ็กเอนด์ของระบบ **Hybrid OMR Exam System** พัฒนาขึ้นบน Google Apps Script (V8 Runtime) ทำงานประสานกับ Google Sheets ในลักษณะ Data Lake / Ledger แบบ Append-Only:
- **`Raw_Scans`**: จัดเก็บประวัติการส่งผลการตรวจข้อสอบทั้งหมด ป้องกันข้อมูลซ้ำซ้อนด้วย `scan_id` (UUIDv4) ผ่านอัลกอริทึม Bounded Window Query (5,000 แถวล่าสุด)
- **`Exams`**: ตารางคลังข้อสอบและเฉลยคำตอบ (Answer Keys)
- **`Students`**: ทะเบียนรายชื่อนักเรียนและห้องเรียน
- **`Room_XXX`**: ชีตแสดงผลคะแนนรายห้องเรียน สร้างขึ้นอัตโนมัติตามห้องเรียนที่พบใน `Students` พร้อมสูตรคำนวณแบบ Real-time (`XLOOKUP` ค้นหาจากล่างขึ้นบน `search_mode = -1`) ดึงผลสอบล่าสุดของนักเรียนแต่ละคนทันทีที่มีการสแกน
- **`Index`**: หน้าแดชบอร์ดสรุปภาพรวมผลการสอบของทุกห้องเรียน (จำนวนผู้เข้าสอบ, ขาดสอบ, ค่าเฉลี่ย, สูงสุด, ต่ำสุด, อัตราการสอบผ่าน)

---

## 2. โครงสร้างไฟล์
```
gas-backend/
├── Code.js              # โค้ดหลัก Apps Script (doGet, doPost, loadScanIds_, provisionMultiRoomViews_)
├── appsscript.json      # การตั้งค่าสิทธิ์และความปลอดภัย (Execute as: USER_DEPLOYING / Access: MYSELF)
├── README.md            # คู่มือการติดตั้ง การ Deploy และคำสั่งทดสอบ curl
└── tests/
    └── gas_backend.test.js  # ชุดทดสอบ Unit Test ครอบคลุม 4 Acceptance Criteria
```

---

## 3. สิทธิ์และความปลอดภัย (Security & Authorization)
ระบบใช้มาตรฐานความปลอดภัยระดับสูงสุด:
- **การ Deploy Web App**: 
  - `Execute as`: **Me (USER_DEPLOYING)**
  - `Who has access`: **Only myself (MYSELF)**
- **การยืนยันตัวตนจาก Android App**:
  - ส่งผ่าน Header: `Authorization: Bearer <Google_OAuth_Access_Token>`
  - ใช้ Google Identity / Google Play Services Auth API ในการขอ Access Token ที่มี Scope สำหรับการเข้าถึง Web App
  - **ไม่มีการเก็บ Secret Key หรือ HMAC ไว้ในโค้ดฝั่ง Client**

---

## 4. ขั้นตอนการติดตั้งและ Deploy (Deployment Guide)

### ขั้นตอนที่ 1: เตรียม Google Spreadsheet
Spreadsheet เป้าหมาย:
[https://docs.google.com/spreadsheets/d/1KcL20IBw5r36ODy9lcsBal6SmeZ9rRitctotZfwJKH4/edit](https://docs.google.com/spreadsheets/d/1KcL20IBw5r36ODy9lcsBal6SmeZ9rRitctotZfwJKH4/edit)

### ขั้นตอนที่ 2: ติดตั้งสคริปต์ใน Google Apps Script
1. เปิด Google Spreadsheet ด้านบน
2. ไปที่เมนู **ส่วนขยาย (Extensions)** > **Apps Script**
3. คัดลอกโค้ดจากไฟล์ `gas-backend/Code.js` ไปวางในโปรเจกต์ Apps Script
4. ไปที่ **การตั้งค่าโปรเจกต์ (Project Settings)** (ไอคอนฟันเฟืองทางซ้าย)
5. ติ๊กเลือก **"แสดงไฟล์ Manifest 'appsscript.json' ในตัวแก้ไข" (Show "appsscript.json" manifest file in editor)**
6. เปิดไฟล์ `appsscript.json` ในตัวแก้ไข แล้ววางเนื้อหาจาก `gas-backend/appsscript.json` ดังนี้:
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
7. กด **บันทึก (Save)**

### ขั้นตอนที่ 3: เริ่มต้นสร้างชีตและข้อมูลตัวอย่าง (One-Click Setup)
ใน Apps Script Editor:
- เลือกฟังก์ชัน `seedInitialDataIfEmpty_` แล้วกด **เรียกใช้ (Run)** เพื่อสร้างชีต `Exams` และ `Students` พร้อมข้อมูลตัวอย่าง
- เลือกฟังก์ชัน `provisionMultiRoomViews_` แล้วกด **เรียกใช้ (Run)** เพื่อสร้างแท็บ `Index` และ `Room_XXX`
*(หรือสามารถเรียกใช้ผ่านฟังก์ชันทดสอบ `runGasUnitTests` ซึ่งจะทำงานทุกขั้นตอนให้โดยอัตโนมัติ)*

### ขั้นตอนที่ 4: การนำไปใช้งานเป็นเว็บแอป (Deploy Web App)
1. คลิกปุ่มสีน้ำเงิน **ทำให้ใช้งานได้ (Deploy)** ที่มุมขวาบน > **การทำให้ใช้งานได้รายการใหม่ (New deployment)**
2. คลิกเลือกประเภท: **เว็บแอป (Web app)**
3. ตั้งค่าการนำไปใช้งาน:
   - **คำอธิบาย (Description)**: `Production OMR Backend v6`
   - **ดำเนินการในฐานะ (Execute as)**: `ฉัน (Me / your-google-account@gmail.com)`
   - **ผู้มีสิทธิ์เข้าถึง (Who has access)**: `เฉพาะฉันเท่านั้น (Only myself)`
4. คลิก **ทำให้ใช้งานได้ (Deploy)**
5. ให้สิทธิ์การเข้าถึง (Authorize access) ด้วยบัญชี Google เจ้าของชีต
6. คัดลอก **URL ของเว็บแอป (Web App URL)** เช่น:
   `https://script.google.com/macros/s/AKfycbxXXXXXXXXXXXXXXXXXXXXXXXXXXXXX/exec`

---

## 5. การทดสอบด้วย `curl` (Testing with curl & Bearer Token)

> [!IMPORTANT]
> **เทคนิคสำคัญในการยิง POST ไปยัง Google Apps Script:**
> Google Apps Script Web App จะตอบกลับด้วยรหัสสถานะ `HTTP 302 Found` เพื่อ Redirect ไปยังเซิร์ฟเวอร์ประมวลผล หากใช้ `curl -L` ปกติ `curl` จะแปลงคำขอเป็น `GET` หลังจาก Redirect!  
> **จำเป็นต้องใส่ Flag `--location --post302`** เพื่อบังคับให้ `curl` ส่งข้อมูลแบบ `POST` พร้อม Payload ต่อไปยัง URL ที่ถูก Redirect!

### 5.1 การรับ Google OAuth Access Token สำหรับการทดสอบ
คุณสามารถขอ Access Token ชั่วคราวผ่าน Google Cloud SDK หรือ Google OAuth Playground:
```bash
# กรณีติดตั้ง gcloud CLI:
export ACCESS_TOKEN=$(gcloud auth print-access-token)
```
หรือทดสอบผ่าน Windows PowerShell:
```powershell
$ACCESS_TOKEN = (gcloud auth print-access-token)
```

---

### 5.2 การทดสอบ Health Check (GET)
```bash
curl -L -X GET "https://script.google.com/macros/s/<DEPLOYMENT_ID>/exec" \
  -H "Authorization: Bearer $ACCESS_TOKEN"
```
**ตัวอย่างผลลัพธ์ตอบกลับ (200 OK):**
```json
{
  "success": true,
  "service": "Hybrid OMR Ingestion Backend (v6)",
  "status": "OPERATIONAL",
  "spreadsheet_id": "1KcL20IBw5r36ODy9lcsBal6SmeZ9rRitctotZfwJKH4",
  "bounded_window_limit": 5000,
  "credit": "จัดทำโดย ครูสุวรรณษา เดชรักษา",
  "timestamp": "2026-11-07T13:45:00.000Z"
}
```

---

### 5.3 การทดสอบส่งผลการสแกน (Valid Batch Ingestion - POST)
```bash
curl --location --post302 \
  --request POST "https://script.google.com/macros/s/<DEPLOYMENT_ID>/exec" \
  --header "Authorization: Bearer $ACCESS_TOKEN" \
  --header "Content-Type: application/json" \
  --data '{
    "exam_id": "EXAM_001",
    "device_id": "Pixel_7_Pro_OMR",
    "scans": [
      {
        "scan_id": "f81d4fae-7dec-11d0-a765-00a0c91e6bf6",
        "student_id": "10001",
        "score": 19,
        "total_questions": 20,
        "answers": ["A", "B", "C", "D", "A", "B", "C", "D", "A", "B", "C", "D", "A", "B", "C", "D", "A", "B", "C", "A"],
        "metrics": {
          "confidence": 0.985,
          "skew_angle": 0.24,
          "processing_time_ms": 42
        },
        "scanned_at": "2026-11-07T13:30:00Z"
      },
      {
        "scan_id": "c9a646d3-9c61-4cb7-bf7d-64bb5a9c445f",
        "student_id": "10002",
        "score": 15,
        "total_questions": 20,
        "answers": ["A", "B", "C", "A", "A", "B", "C", "D", "A", "B", "C", "D", "A", "B", "C", "D", "A", "B", "C", "D"],
        "metrics": {
          "confidence": 0.941,
          "skew_angle": 0.15,
          "processing_time_ms": 38
        },
        "scanned_at": "2026-11-07T13:30:15Z"
      }
    ]
  }'
```
**ตัวอย่างผลลัพธ์ตอบกลับ (200 OK):**
```json
{
  "success": true,
  "exam_id": "EXAM_001",
  "total_received": 2,
  "inserted": 2,
  "duplicates": 0,
  "inserted_scan_ids": [
    "f81d4fae-7dec-11d0-a765-00a0c91e6bf6",
    "c9a646d3-9c61-4cb7-bf7d-64bb5a9c445f"
  ],
  "credit": "จัดทำโดย ครูสุวรรณษา เดชรักษา",
  "synced_at": "2026-11-07T13:46:10.123Z"
}
```

---

### 5.4 การทดสอบระบบตัดข้อมูลซ้ำ (Deduplication Test - POST)
ส่งคำขอเดิมซ้ำอีกครั้ง (มี `scan_id` ที่เคยถูกบันทึกไปแล้ว):
```bash
curl --location --post302 \
  --request POST "https://script.google.com/macros/s/<DEPLOYMENT_ID>/exec" \
  --header "Authorization: Bearer $ACCESS_TOKEN" \
  --header "Content-Type: application/json" \
  --data '{
    "exam_id": "EXAM_001",
    "device_id": "Pixel_7_Pro_OMR",
    "scans": [
      {
        "scan_id": "f81d4fae-7dec-11d0-a765-00a0c91e6bf6",
        "student_id": "10001",
        "score": 19,
        "total_questions": 20
      }
    ]
  }'
```
**ตัวอย่างผลลัพธ์ตอบกลับ (ตรวจจับข้อมูลซ้ำสำเร็จ):**
```json
{
  "success": true,
  "exam_id": "EXAM_001",
  "total_received": 1,
  "inserted": 0,
  "duplicates": 1,
  "inserted_scan_ids": [],
  "credit": "จัดทำโดย ครูสุวรรณษา เดชรักษา",
  "synced_at": "2026-11-07T13:47:00.456Z"
}
```

---

### 5.5 การทดสอบปฏิเสธข้อสอบที่ไม่รู้จัก (Unknown Exam Rejection)
```bash
curl --location --post302 \
  --request POST "https://script.google.com/macros/s/<DEPLOYMENT_ID>/exec" \
  --header "Authorization: Bearer $ACCESS_TOKEN" \
  --header "Content-Type: application/json" \
  --data '{
    "exam_id": "EXAM_INVALID_9999",
    "scans": [
      {
        "scan_id": "b3e0c0a8-b63e-4fa6-8f3b-826f423ab1a1",
        "student_id": "10001",
        "score": 10,
        "total_questions": 20
      }
    ]
  }'
```
**ตัวอย่างผลลัพธ์ตอบกลับ (404 Not Found):**
```json
{
  "success": false,
  "error": "ERR_UNKNOWN_EXAM",
  "message": "Exam ID \"EXAM_INVALID_9999\" is not registered in the Exams sheet.",
  "credit": "จัดทำโดย ครูสุวรรณษา เดชรักษา"
}
```

---

### 5.6 การทดสอบสั่งสร้างหรืออัปเดตห้องเรียน (Multi-Room Provisioning)
```bash
curl --location --post302 \
  --request POST "https://script.google.com/macros/s/<DEPLOYMENT_ID>/exec" \
  --header "Authorization: Bearer $ACCESS_TOKEN" \
  --header "Content-Type: application/json" \
  --data '{"action": "provision"}'
```
**ตัวอย่างผลลัพธ์ตอบกลับ:**
```json
{
  "success": true,
  "action": "provision",
  "result": {
    "status": "SUCCESS",
    "rooms_provisioned": 2,
    "rooms": [
      {
        "room_name": "ม.5/1",
        "sheet_name": "Room_ม.5_1",
        "sheet_id": 101,
        "student_count": 5
      },
      {
        "room_name": "ม.5/2",
        "sheet_name": "Room_ม.5_2",
        "sheet_id": 102,
        "student_count": 5
      }
    ],
    "credit": "จัดทำโดย ครูสุวรรณษา เดชรักษา"
  },
  "credit": "จัดทำโดย ครูสุวรรณษา เดชรักษา"
}
```

---

## 6. การทดสอบแบบ Unit Test ในเครื่อง (Local Unit Testing)
สามารถรันการทดสอบ Unit Test จำลองสภาพแวดล้อม Google Apps Script ได้ทันที:
```bash
node gas-backend/tests/gas_backend.test.js
```
ชุดทดสอบจะทำการทดสอบ Acceptance Criteria ทั้ง 4 ข้อ:
1. การบันทึกข้อมูล Scan แบบกลุ่มที่ถูกต้องลงใน `Raw_Scans`
2. การคัดกรองข้อมูลซ้ำตาม `scan_id` ผ่าน Bounded Window Query
3. การสร้างหน้า `Index` และแท็บห้องเรียน `Room_XXX` อัตโนมัติ พร้อมสูตรคำนวณ `XLOOKUP`
4. การปฏิเสธคำขอที่ไม่ถูกต้อง (Bad Format / Unknown Exam)

---
**จัดทำโดย ครูสุวรรณษา เดชรักษา**

