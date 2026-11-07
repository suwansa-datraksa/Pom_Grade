# ระบบตรวจข้อสอบ OMR (Android Kotlin + Web Console บน GitHub Pages + Google Sheets)
## เอกสารสถาปัตยกรรม ข้อมูล ความปลอดภัย และ Master Prompt แบบแบ่งเฟส (v6 - Multi-Room Sheets & Curve-Proof Timing Tracks)

เอกสารฉบับนี้อัปเกรดจาก v3.1 และ v5 โดยรวบรวมคุณสมบัติสำคัญ:
1. **การรักษาความปลอดภัยระดับองค์กร:** ยืนยันตัวตนด้วย Google OAuth ผ่าน Apps Script แบบ "Only myself"
2. **คณิตศาสตร์ชดเชยกระดาษโค้งย่น (Dual-Sided Timing Tracks):** ดัดระนาบรายแถวด้วยเวกเตอร์การประมาณค่าในช่วง (Local Interpolation Vector $\overset{\rightharpoonup}{P}_i(t)$)
3. **การจัดโครงสร้างชีตสำหรับครูผู้สอน (Teacher-Friendly Multi-Room Gradebook):** จัดเก็บข้อมูลดิบแบบ Append-Only ใน `Raw_Scans` เพื่อความโปร่งใสและตรวจสอบย้อนหลังได้ พร้อมระบบสารบัญอัตโนมัติ (`Index`), ทะเบียนนักเรียนแยกเฉพาะ (`Students`), และตารางเกรดแยก 1 ห้อง 1 แท็บ (`Room_XXX`) ที่ดึงคะแนนล่าสุดเรียงตามเลขที่อัตโนมัติ
4. **แก้ปัญหาทางเทคนิคสำคัญ:** ป้องกันการตัด Header `Authorization` ขณะเกิด HTTP 302 Redirect ใน Android HTTP Client

---

## 0. สรุปความแตกต่างและการพัฒนาในแต่ละเวอร์ชัน

| หัวข้อ | v3.1 | v4 (เปรียบเทียบ) | v6 (ฉบับนี้) |
|---|---|---|---|
| **การยืนยันตัวตน** | Google OAuth (Only myself) | HMAC-SHA256 (Secret รั่วบนเว็บ) | **Google OAuth (Only myself)** |
| **การจัดเก็บข้อมูล** | Append-Only (ดูในชีตยาก) | Smart Upsert (เสี่ยงข้อมูลหาย) | **Append-Only ใน `Raw_Scans` + Auto Multi-Room Sheets (1 ห้อง 1 แท็บ)** |
| **หน้าแสดงผลในชีต** | รวมในแท็บเดียว | รวมในแท็บเดียว | **มีหน้าสารบัญ (`Index`) + หน้าทะเบียน ID (`Students`) + แท็บแยกห้อง (`Room_XXX`)** |
| **การแก้กระดาษโค้ง** | บรรยายขั้นตอน Timing Tracks | สมการเวกเตอร์ $\overset{\rightharpoonup}{P}_i(t)$ | **สมการเวกเตอร์ $\overset{\rightharpoonup}{P}_i(t)$ + Asymmetric Bar Widths + ตรวจสอบ Residual** |
| **การส่งผ่าน HTTP** | ไม่ระบุการจัดการ Redirect | ยิงส่งตรง | **OkHttp Custom Redirect Interceptor รักษา Authorization Header** |
| **โครงสร้าง Master Prompt** | แบ่ง 6 เฟส มี Acceptance Criteria | Master Prompt ก้อนเดียว | **แบ่ง 6 เฟส พร้อม Acceptance Criteria คลุม Multi-Room Generator** |

---

## 1. การตัดสินใจหลักทางสถาปัตยกรรม (Architectural Decisions)

1. **Android Kotlin Native Only:** ไม่ใช้ Flutter, React Native หรือ WebView เพื่อประสิทธิภาพสูงสุดของ OpenCV และ CameraX
2. **ความปลอดภัยไร้ความลับ (Zero-Secret Client):**
   * ฝั่งแอปใช้ Google Authorization API ขอ Access Token ของบัญชีเจ้าของ แนบไปกับ Header `Authorization: Bearer <token>`
   * ฝั่ง Web Console บน GitHub Pages ล็อกอินผ่าน Google Identity Services (GIS) และอ่านชีตผ่าน Google Sheets API (Read-only) โดยตรง
   * ไม่มี Secret Key ใด ๆ ฝังในโค้ด C++, Kotlin, หรือ JavaScript
3. **Apps Script Execution Control:** ตั้งค่า Web App เป็น **Execute as: Me** และ **Who has access: Only myself**
4. **Append-Only Audit Trail + Multi-Room Views:**
   * สคริปต์บันทึกทุกข้อมูลการสแกนลงแท็บ `Raw_Scans` แบบต่อท้าย (Append-only) ไม่มีการลบหรือเขียนทับ เพื่อเก็บประวัติการตรวจสอบย้อนหลังที่เชื่อถือได้
   * ระบบสร้างและดูแลแท็บห้องเรียน (`Room_XXX`) โดยใช้สูตรไดนามิก ดึงเฉพาะคะแนนล่าสุดของนักเรียนแต่ละคนในห้องนั้น ๆ มาแสดงผล 1 บรรทัดต่อนักเรียน 1 คน เรียงตามเลขที่อัตโนมัติ
5. **Server-Side Grading:** ข้อมูลเฉลยลับถูกเก็บในแท็บ `Protected_Keys` เท่านั้น โทรศัพท์ส่งเพียงคำตอบดิบ และคำนวณคะแนนบนเซิร์ฟเวอร์

---

## 2. แผนภาพสถาปัตยกรรมระบบ (System Architecture)

```
┌──────────────────────────────────────────────────────────────────────────────────┐
│                   WEB MANAGEMENT CONSOLE (GitHub Pages, static)                  │
│  - Template Generator: jsPDF + qrcode.js (Dual-Sided Timing Tracks & Anchors)   │
│  - Dashboard & Analytics: Google Sign-in (GIS) -> Sheets API (Read-only)         │
│  - Item Analysis: p, D, KR-20 คำนวณในหน่วยความจำเบราว์เซอร์                      │
└────────────────────────────────────────┬─────────────────────────────────────────┘
                                         │ Sheets API (Bearer Token ใน RAM)
                                         ▼
┌──────────────────────────────────────────────────────────────────────────────────┐
│                   GOOGLE SHEETS (จำกัดสิทธิ์เฉพาะบัญชีเจ้าของ)                    │
│   [Index]            - หน้าสารบัญ สรุปยอดส่ง/ขาดสอบ และลิงก์ไปยังห้องต่าง ๆ       │
│   [Students]         - ทะเบียนรหัสประจำตัว ชื่อ-สกุล ชั้น และเลขที่                │
│   [Room_4-1]...      - หน้ารายงานคะแนนรายห้อง 1 บรรทัดต่อคน เรียงตามเลขที่          │
│   [Protected_Keys]   - เฉลยลับ เกณฑ์การให้คะแนน และจำนวนข้อ                        │
│   [Raw_Scans]        - Log ดิบทุกประวัติการสแกนแบบ Append-Only (Idempotency Key)   │
└────────────────────────────────────────▲─────────────────────────────────────────┘
                                         │ เขียนผ่าน Apps Script เท่านั้น
┌────────────────────────────────────────┴─────────────────────────────────────────┐
│        GOOGLE APPS SCRIPT WEB APP (Execute as: Me / Who has access: Only myself) │
│  - ประตูคัดกรอง: ตรวจสอบ Google OAuth Token ก่อนเข้าสู่การรันโค้ด                │
│  - Concurrency Lock: LockService.tryLock(15000)                                  │
│  - Deduplication: ตรวจสอบความซ้ำซ้อนด้วย scan_id (UUID)                          │
│  - Server-Side Grading: ตรวจคะแนนและคัดแยก Flag (OK / NEEDS_REVIEW)             │
│  - Auto Room Provisioning: สร้างแท็บห้องเรียนและหน้า Index อัตโนมัติ             │
└────────────────────────────────────────▲─────────────────────────────────────────┘
                                         │ HTTPS POST + Authorization: Bearer
                                         │ Follow Redirect 302 + Retain Auth Header
┌────────────────────────────────────────┴─────────────────────────────────────────┐
│                 ANDROID SCANNER (Kotlin Native + CameraX + OpenCV)               │
│  - Snapshot-only High-Res Capture + Torch Autofocus                              │
│  - Dual-Stage Curve-Proof OMR: Global Homography + Row-by-Row Interpolation      │
│  - Local Sandbox Storage: Room (SQLite) + SQLCipher + AllowBackup=false          │
│  - Offline-First Engine: WorkManager Queue + Jittered Exponential Backoff        │
│  - Google Authorization API: Silent Token Fetching & Refresh                     │
└──────────────────────────────────────────────────────────────────────────────────┘
```

---

## 3. โครงสร้างและการจัดระเบียบ Google Sheets (Multi-Room Gradebook)

ระบบจัดแบ่งแท็บออกเป็น 3 กลุ่ม: สารบัญ/รายงานผู้ใช้, ฐานข้อมูลหลัก, และข้อมูลระบบ

```
[Index] ──► [Students] ──► [Room_4-1] ──► [Room_4-2] ──► [Protected_Keys] ──► [Raw_Scans]
(สารบัญ)    (ทะเบียน ID)    (คะแนน ม.4/1)   (คะแนน ม.4/2)      (เฉลยลับ)         (Log ดิบ)
```

### 3.1 แท็บ `Index` (หน้าสารบัญและภาพรวม)
สร้างลิงก์และสูตรสรุปยอดแบบไดนามิก:
* **คอลัมน์ A (ห้องเรียน):** เช่น `ม.4/1`, `ม.4/2`
* **คอลัมน์ B (ลิงก์ชีต):** สูตร Hyperlink ตรงไปยังชีตห้องเรียนนั้น:
  ```text
  =HYPERLINK("#gid=" & VLOOKUP(A2, Internal_Gid_Map, 2, FALSE), "เปิดดูคะแนน " & A2)
  ```
* **คอลัมน์ C (จำนวนนักเรียน):** `=COUNTA('Room_4-1'!A3:A)`
* **คอลัมน์ D (ส่งแล้ว):** `=COUNTIF('Room_4-1'!E3:E, ">=0")`
* **คอลัมน์ E (ขาดสอบ):** `=COUNTIF('Room_4-1'!H3:H, "*ยังไม่พบผลสแกน*")`
* **คอลัมน์ F (คะแนนเฉลี่ย):** `=AVERAGE('Room_4-1'!E3:E)`

### 3.2 แท็บ `Students` (ทะเบียนรหัสประจำตัวนักเรียน)
เป็นตาราง Master Data ของโรงเรียน กำหนดคอลัมน์ `StudentCode` เป็น **Plain text (`@`)** เสมอ:
* `StudentCode` (Text, 5 หลัก)
* `Name` (Text)
* `Classroom` (Text เช่น `"4/1"`, `"4/2"`)
* `SeatNo` (Number)

### 3.3 แท็บห้องเรียน (`Room_4-1`, `Room_4-2`, ...)
จัดทำตารางเกรดสำหรับครูประจำวิชา 1 บรรทัดต่อนักเรียน 1 คน:
* **เซลล์ B1:** รหัสชุดข้อสอบ (`ExamID`) เช่น `"PHYS40-2026A"` (กำหนดเป็น Plain text เพื่อดึงผลสอบเฉพาะวิชา)
* **หัวตาราง (แถว 2):** `เลขที่ | รหัสนักเรียน | ชื่อ - นามสกุล | คำตอบที่อ่านได้ | คะแนน | เต็ม | เวลาสแกน | สถานะ`
* **คอลัมน์ A, B, C (แถว 3 เป็นต้นไป):** ดึงรายชื่อนักเรียนเฉพาะห้องนั้นจากแท็บ `Students` เรียงตามเลขที่:
  * เซลล์ A3: `=IFERROR(FILTER(Students!D2:D, Students!C2:C = "4/1"), "")`
  * เซลล์ B3: `=IFERROR(FILTER(Students!A2:A, Students!C2:C = "4/1"), "")`
  * เซลล์ C3: `=IFERROR(FILTER(Students!B2:B, Students!C2:C = "4/1"), "")`
* **คอลัมน์ D–H (ข้อมูลผลการสแกน):** ค้นหาและดึงแถวล่าสุดจาก `Raw_Scans` โดยอิงตามรหัสนักเรียนและชุดข้อสอบ:
  ```text
  =MAP(B3:INDEX(B3:B, COUNTA(B3:B)), LAMBDA(sid, 
    IF(sid="", {"", "", "", "", ""}, 
    IFERROR(
      CHOOSEROWS(
        FILTER(
          CHOOSECOLS(Raw_Scans!A$2:J, 9, 7, 8, 2, 10), 
          Raw_Scans!E$2:E = $B$1, 
          Raw_Scans!F$2:F = sid
        ), 
        -1
      ), 
      {"-", "-", "-", "-", "⚠️ ยังไม่พบผลสแกน"}
    ))))
  ```

### 3.4 แท็บระบบ (`Raw_Scans` และ `Protected_Keys`)
* **`Protected_Keys`:** `ExamID | AnswerKey | Weights | TotalQ` (ตั้งค่าเป็น Plain text ห้ามผู้ไม่เกี่ยวข้องเข้าถึง)
* **`Raw_Scans`:** จัดเก็บข้อมูลดิบ 10 คอลัมน์:
  `ScanID | ScannedAt | ReceivedAt | DeviceID | ExamID | StudentCode | Score | MaxScore | Answers | Status`

---

## 4. อัลกอริทึม OMR ชดเชยกระดาษโค้งย่น (Dual-Sided Timing Tracks Engine)

### 4.1 ข้อจำกัดของ Homography 4 มุม
การแปลงพิกัด Perspective Transform (Homography) 4 มุม สมมติว่ากระดาษเป็นระนาบแข็ง ($2\text{D}$ Rigid Plane) เมื่อกระดาษเกิดการโก่งตัวเป็นลอนคลื่น (Cylindrical Sagging) หรือเกิดรอยยับตามแนวนอน ตำแหน่งกึ่งกลางแผ่น (แถวที่ 10–30) จะเกิดการคลาดเคลื่อนออกจากพิกัดกริดที่คาดการณ์ไว้

### 4.2 โครงสร้างแถบ Timing Tracks
* **Corner Markers 4 มุม:** มุมซ้ายบนใช้สี่เหลี่ยมขนาด $14 \times 14\text{ mm}$ ส่วนมุมอื่นใช้ $8 \times 8\text{ mm}$ เพื่อตรวจจับและหมุนภาพแก้อาการสแกนกลับหัว $180^\circ$
* **Left & Right Timing Tracks:** แถบสี่เหลี่ยมทึบแนวนอนขนาบข้างแถวคำถาม 1 แถบต่อ 1 แถวข้อสอบ
  * ขอบซ้ายกว้าง $5\text{ mm}$ สูง $1.6\text{ mm}$
  * ขอบขวากว้าง $3\text{ mm}$ สูง $1.6\text{ mm}$ (Asymmetric Widths เพื่อแยกแยะกรณีภาพกลับกระจกหรือพลิกด้าน)
  * ระยะห่างจากขอบกระดาษ $\ge 8\text{ mm}$ เพื่อให้อยู่ในระยะพิมพ์ของเครื่องพิมพ์เลเซอร์/อิงก์เจ็ตทั่วไป

```
┌────┐                                                         ┌────┐
│ ■  │ █ █ █ █ █ █ █ █ █ █  QR CODE  █ █ █ █ █ █ █ █ █ █ █ █ █ │ ■  │ (Upper Markers: 14mm vs 8mm)
└────┘                                                         └────┘
  ▌                                                              ▎
  ▌ [Row 1]   (A)  (B)  (C)  (D)        (A)  (B)  (C)  (D)        ▎  <-- Timing Bars ซ้าย-ขวา
  ▌ [Row 2]   (A)  (B)  (C)  (D)        (A)  (B)  (C)  (D)        ▎      ความกว้างอสมมาตร (5mm vs 3mm)
  ...                                                             ...
  ▌ [Row 25]  (A)  (B)  (C)  (D)        (A)  (B)  (C)  (D)        ▎
┌────┐                                                         ┌────┐
│ ■  │                                                         │ ■  │ (Lower Markers: 8mm)
└────┘                                                         └────┘
```

### 4.3 การประมาณค่าพิกัดรายแถว (Row-by-Row Local Interpolation)
กระบวนการประมวลผลบน Android APK แบ่งเป็น 2 ขั้นตอน:

1. **Global Homography Warp:** ดัดระนาบเบื้องต้นด้วยมาร์กเกอร์ 4 มุมเข้าสู่ขนาดอ้างอิง $1000 \times 1414\text{ px}$
2. **Timing Track Extraction:** 
   * ตัด Strip ROI บริเวณขอบซ้ายและขวาตาม Template พิกัด
   * ทำ Adaptive Thresholding และ Connected Components เพื่อหาพิกัดจุดศูนย์ถ่วง (Centroid) ของแถบแต่ละแถว $i$ จะได้พิกัดจริง $P_{L, i} = (x_{L, i}, y_{L, i})$ และ $P_{R, i} = (x_{R, i}, y_{R, i})$
3. **Scanline Bubble Interpolation:**
   สำหรับคำถามในแถวที่ $i$ พิกัดจะไม่ยึดตามแกน $y$ คงที่ แต่จะคำนวณตามแนวเส้นเวกเตอร์ที่เชื่อมต่อระหว่าง $P_{L, i}$ และ $P_{R, i}$:
   $$\overset{\rightharpoonup}{P}_i(t) = (1 - t) \begin{pmatrix} x_{L, i} \\ y_{L, i} \end{pmatrix} + t \begin{pmatrix} x_{R, i} \\ y_{R, i} \end{pmatrix}, \quad t \in [0, 1]$$
   โดย $t$ คือสัดส่วนตำแหน่งแกน $x$ ปกติของแต่ละช้อยส์จาก Template
4. **การตรวจสอบความคลาดเคลื่อน (Residual Validation):**
   * หากตรวจไม่พบ Timing Bar บางแถว ให้ใช้การประมาณค่าจากแถวข้างเคียง (Linear Spline) แต่หากแถวที่หายไปเกิน $10\%$ ของจำนวนแถวทั้งหมด ให้ยกเลิกการประมวลผลและเตือนให้ผู้ใช้ถ่ายภาพใหม่
   * คำนวณ Standard Deviation ของระยะห่างในแนวดิ่ง ($\Delta y$) หากค่าเบี่ยงเบนเกินพิกัดที่กำหนด ให้แจ้งเตือน `"กระดาษโค้งงอมากเกินไป กรุณารีดให้เรียบแล้วถ่ายใหม่"` และกำหนดสถานะเป็น `NEEDS_REVIEW`

### 4.4 เกณฑ์การตัดสินช้อยส์และรหัสนักเรียน (Decision Engine)
* **เกณฑ์สำหรับช้อยส์ข้อสอบ (4 หรือ 5 ตัวเลือก):**
  คำนวณ Fill Ratio ($f$) ของวงกลมแกนกลาง โดยให้ $f_1$ คือค่าสูงสุด และ $f_2$ คือค่าอันดับสอง, กำหนด $\Delta = f_1 - f_2$:
  1. หาก $f_1 < 0.20$ $\rightarrow$ สัญลักษณ์ `-` (เว้นว่าง / BLANK)
  2. หาก $f_2 \ge 0.35$ และ $\Delta < 0.25$ $\rightarrow$ สัญลักษณ์ `*` (ระบายหลายข้อ / MULTIPLE)
  3. หาก $f_1 \ge 0.45$ หรือ $\Delta \ge 0.25$ $\rightarrow$ ตอบช้อยส์ที่มีค่า $f_1$ (เอาชนะรอยลบดินสอที่จางกว่า)
  4. นอกเหนือจากนี้ $\rightarrow$ สัญลักษณ์ `?` (UNCERTAIN ตั้งสถานะเป็น `NEEDS_REVIEW`)
* **เกณฑ์สำหรับกริดรหัสนักเรียน (5 หลัก หลักละ 0–9):**
  * แต่ละหลักต้องมีตัวเลือกที่ผ่านเกณฑ์ $f \ge 0.45$ และ $\Delta \ge 0.25$ เพียงค่าเดียว
  * หากหลักใดไม่มีรอยระบาย หรือระบายซ้ำซ้อนมากกว่า 1 จุด แอปจะหยุดการทำงานและเปิดหน้าต่างยืนยันรหัสนักเรียน (Manual Confirmation Prompt) ให้ผู้ใช้ระบุรหัสก่อนบันทึกลงฐานข้อมูล

---

## 5. สถาปัตยกรรมการสื่อสารและการจัดการเครือข่าย

### 5.1 การจัดการ HTTP 302 Redirect ใน Android Client
Google Apps Script เมื่อถูกเรียกผ่าน HTTP POST ไปยัง URL `/exec` จะตอบกลับด้วยรหัส **HTTP 302 Found** เพื่อ Redirect ไปยังโดเมน `script.googleusercontent.com` ไลบรารี HTTP ทั่วไปจะตัด Header `Authorization` ทิ้งระหว่าง Cross-Domain Redirect ส่งผลให้คำขอล้มเหลวด้วยรหัส 401/403

**ข้อกำหนดการตั้งค่า OkHttpClient:**
```kotlin
val okHttpClient = OkHttpClient.Builder()
    .followRedirects(false)
    .followSslRedirects(false)
    .addInterceptor { chain ->
        val originalRequest = chain.request()
        val response = chain.proceed(originalRequest)
        
        if (response.code == 302 || response.code == 307 || response.code == 308) {
            val redirectUrl = response.header("Location")
            if (!redirectUrl.isNullOrEmpty()) {
                response.close()
                val newRequest = originalRequest.newBuilder()
                    .url(redirectUrl)
                    .method("POST", originalRequest.body)
                    .header("Authorization", originalRequest.header("Authorization") ?: "")
                    .header("Content-Type", "application/json")
                    .build()
                return@addInterceptor chain.proceed(newRequest)
            }
        }
        response
    }
    .connectTimeout(30, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .build()
```

### 5.2 วงจรการทำงานของ Background Sync (`SyncWorker`)
1. **คิวงานออฟไลน์:** เมื่อสแกนเสร็จ ข้อมูลจะถูกเขียนลงตาราง `scans` ในฐานข้อมูล Room ทันทีด้วย `sync_status = 0` (หรือ `3` หากมีข้อ `?`)
2. **WorkManager Constraint:** ทำงานเมื่อต่อสัญญาณอินเทอร์เน็ต (`NetworkType.CONNECTED`)
3. **การขอ Token:** เรียกขอ Access Token จาก Google Authorization API แบบเงียบ (Silent Sign-In) หาก Token หมดอายุและ Refresh ไม่สำเร็จ จะส่ง Notification แจ้งเตือน `"กรุณาเปิดแอปเพื่อยืนยันตัวตน Google ใหม่"`
4. **Batch Transmission:** ส่งข้อมูลแบบชุดไม่เกิน 50 แถวต่อคำขอ พร้อมเพิ่มค่า Random Jitter (100–1000 ms) ใน Exponential Backoff เพื่อป้องกันปัญหาเครื่องลูกข่ายแย่ง Lock บน Apps Script พร้อมกัน

---

## 6. ซอร์สโค้ดฝั่งคลาวด์ (`gas-backend/Code.js`)

```javascript
const SHEET_KEYS = 'Protected_Keys';
const SHEET_STUDENTS = 'Students';
const SHEET_SCANS = 'Raw_Scans';
const SHEET_INDEX = 'Index';

const MAX_RECORDS = 200;
const MAX_BODY_CHARS = 250000;
const SCAN_HEADERS = ['ScanID', 'ScannedAt', 'ReceivedAt', 'DeviceID', 'ExamID',
                      'StudentCode', 'Score', 'MaxScore', 'Answers', 'Status'];
const TEXT_COLS = [1, 2, 3, 4, 5, 6, 9, 10]; // คอลัมน์ที่ต้องเป็น Plain text

const RE_ID = /^[A-Za-z0-9._-]{8,64}$/;
const RE_EXAM = /^[A-Za-z0-9._-]{1,40}$/;
const RE_STUDENT = /^[0-9]{5}$/;
const RE_ANS = /^[0-4\-*?]+$/;

function doGet() {
  return json_({ status: 'OK', version: 'v6-multi-room' });
}

function doPost(e) {
  const lock = LockService.getScriptLock();
  if (!lock.tryLock(15000)) return json_({ status: 'BUSY' });

  try {
    if (!e || !e.postData || !e.postData.contents) {
      return json_({ status: 'BAD_REQUEST', message: 'empty body' });
    }
    const raw = e.postData.contents;
    if (raw.length > MAX_BODY_CHARS) return json_({ status: 'BAD_REQUEST', message: 'body too large' });

    let payload;
    try { payload = JSON.parse(raw); } catch (err) {
      return json_({ status: 'BAD_REQUEST', message: 'invalid json' });
    }
    if (!payload || !Array.isArray(payload.records) || payload.records.length === 0 || payload.records.length > MAX_RECORDS) {
      return json_({ status: 'BAD_REQUEST', message: 'invalid records' });
    }

    const ss = SpreadsheetApp.getActiveSpreadsheet();
    const keys = loadKeys_(ss);
    const studentsMap = loadStudentsMap_(ss);
    const scansSheet = getScansSheet_(ss);
    const seen = loadScanIds_(scansSheet);
    const receivedAt = new Date().toISOString();

    const rows = [];
    const results = [];

    payload.records.forEach(function (r) {
      const id = r && typeof r.scan_id === 'string' ? r.scan_id : '';
      if (!RE_ID.test(id)) { results.push({ scan_id: id, result: 'REJECTED', reason: 'BAD_FORMAT' }); return; }
      if (seen[id]) { results.push({ scan_id: id, result: 'DUPLICATE' }); return; }

      const examId = String(r.exam_id || '');
      const student = String(r.student_code || '');
      const deviceId = String(r.device_id || '');
      const ans = String(r.ans || '');
      const scannedAt = Number(r.scanned_at);

      if (!RE_EXAM.test(examId) || !RE_STUDENT.test(student) || !RE_ID.test(deviceId) ||
          !RE_ANS.test(ans) || !isFinite(scannedAt)) {
        results.push({ scan_id: id, result: 'REJECTED', reason: 'BAD_FORMAT' }); return;
      }
      const k = keys[examId];
      if (!k) { results.push({ scan_id: id, result: 'REJECTED', reason: 'UNKNOWN_EXAM' }); return; }
      if (ans.length !== k.key.length) { results.push({ scan_id: id, result: 'REJECTED', reason: 'BAD_LENGTH' }); return; }

      let score = 0, max = 0;
      for (let q = 0; q < k.key.length; q++) {
        const w = k.weights[q];
        max += w;
        if (ans.charAt(q) === k.key.charAt(q)) score += w;
      }

      let flag = 'OK';
      if (ans.indexOf('?') !== -1) flag = 'NEEDS_REVIEW';
      else if (studentsMap.size > 0 && !studentsMap.has(student)) flag = 'UNKNOWN_STUDENT';

      seen[id] = true;
      rows.push([id, new Date(scannedAt).toISOString(), receivedAt, deviceId, examId,
                 student, score, max, ans, flag]);
      results.push({ scan_id: id, result: 'ACCEPTED', score: score, max_score: max, flag: flag });
    });

    if (rows.length > 0) {
      const start = scansSheet.getLastRow() + 1;
      TEXT_COLS.forEach(function (c) {
        scansSheet.getRange(start, c, rows.length, 1).setNumberFormat('@');
      });
      scansSheet.getRange(start, 1, rows.length, SCAN_HEADERS.length).setValues(rows);
      SpreadsheetApp.flush();

      // ตรวจสอบและสร้างโครงสร้างแท็บแยกห้องเรียนอัตโนมัติ
      provisionMultiRoomViews_(ss, studentsMap);
    }

    return json_({ status: 'SUCCESS', results: results });

  } catch (err) {
    console.error(err);
    return json_({ status: 'ERROR', message: err.toString() });
  } finally {
    lock.releaseLock();
  }
}

function json_(obj) {
  return ContentService.createTextOutput(JSON.stringify(obj))
    .setMimeType(ContentService.MimeType.JSON);
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

function loadKeys_(ss) {
  const sh = ss.getSheetByName(SHEET_KEYS);
  const map = {};
  if (!sh) return map;
  const data = sh.getDataRange().getValues();
  for (let i = 1; i < data.length; i++) {
    const examId = String(data[i][0]).trim();
    const key = String(data[i][1]).trim();
    if (!examId || !RE_ANS.test(key)) continue;
    const wRaw = String(data[i][2] || '').trim();
    let weights = key.split('').map(() => 1);
    if (wRaw) {
      const parsed = wRaw.split(',').map(Number);
      if (parsed.length === key.length && parsed.every(x => isFinite(x) && x >= 0)) {
        weights = parsed;
      }
    }
    map[examId] = { key: key, weights: weights };
  }
  return map;
}

function loadStudentsMap_(ss) {
  const map = new Map();
  const sh = ss.getSheetByName(SHEET_STUDENTS);
  if (!sh) return map;
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

function loadScanIds_(scans) {
  const ids = {};
  const last = scans.getLastRow();
  if (last < 2) return ids;
  const startRow = Math.max(2, last - 5000);
  const numRows = last - startRow + 1;
  scans.getRange(startRow, 1, numRows, 1).getValues().forEach(row => {
    ids[String(row[0])] = true;
  });
  return ids;
}

function provisionMultiRoomViews_(ss, studentsMap) {
  const rooms = new Set();
  studentsMap.forEach(s => { if (s.classroom) rooms.add(s.classroom); });
  if (rooms.size === 0) return;

  const roomList = Array.from(rooms).sort();
  const indexSheet = ss.getSheetByName(SHEET_INDEX) || ss.insertSheet(SHEET_INDEX, 0);

  if (indexSheet.getLastRow() === 0) {
    indexSheet.appendRow(["ห้องเรียน", "ลิงก์เข้าดูข้อมูล", "จำนวนนักเรียน", "ส่งแล้ว", "ยังไม่พบผลสแกน", "คะแนนเฉลี่ย"]);
    indexSheet.getRange("A1:F1").setFontWeight("bold").setBackground("#E5E7EB");
    indexSheet.setFrozenRows(1);
  }

  roomList.forEach((room, idx) => {
    const tabName = "Room_" + room.replace(/[\/\\?*:[\]]/g, '-');
    let rSheet = ss.getSheetByName(tabName);
    
    if (!rSheet) {
      rSheet = ss.insertSheet(tabName);
      rSheet.getRange("A1").setValue("ชุดข้อสอบ (ExamID):");
      rSheet.getRange("B1").setValue("PHYS40-2026A").setNumberFormat('@').setFontWeight("bold");
      
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
  });
}
```

---

## 7. โครงสร้างโฟลเดอร์โปรเจกต์ (Git Monorepo)

```text
omr-hybrid-system/
├── .github/workflows/deploy.yml          # CI/CD สั่ง Deploy Web Console ไปยัง GitHub Pages
├── docs/architecture.md                  # เอกสารฉบับนี้
├── gas-backend/
│   ├── Code.js                           # Web App Backend + Auto Room Provisioning
│   ├── appsscript.json                   # Manifest (Execute as: Me / Only myself)
│   └── README.md                         # ขั้นตอน Deploy และคำสั่ง curl ทดสอบ
├── web-console/                          # Static Web บน GitHub Pages
│   ├── .nojekyll
│   ├── index.html                        # แดชบอร์ดสถิติ p, D, KR-20
│   ├── template-generator.html           # ออกแบบกระดาษคำตอบ A4 PDF พร้อม Timing Tracks
│   ├── css/style.css
│   └── js/{app.js, auth.js, sheets-client.js, pdf-generator.js, analytics.js, config.js}
└── android-scanner/                      # Native Android Kotlin Application
    ├── build.gradle.kts
    ├── app/
    │   ├── build.gradle.kts
    │   ├── proguard-rules.pro
    │   └── src/
    │       ├── main/
    │       │   ├── AndroidManifest.xml   # allowBackup=false, exported=false
    │       │   ├── java/com/school/omrscanner/
    │       │   │   ├── MainActivity.kt
    │       │   │   ├── CameraActivity.kt
    │       │   │   ├── ReviewActivity.kt
    │       │   │   ├── omr/{MarkerDetector, TimingTrackDetector, ScanlineInterpolator, BubbleEvaluator, AnswerEncoder}.kt
    │       │   │   ├── template/{TemplateModel, TemplateValidator}.kt
    │       │   │   ├── data/local/{AppDatabase, ScanDao, ScanEntity}.kt
    │       │   │   ├── auth/GoogleAuthProvider.kt
    │       │   │   └── sync/{OkHttpProvider, GasApiService, SyncWorker}.kt
    │       │   └── res/
    │       │       ├── layout/
    │       │       ├── raw/{beep_success.mp3, beep_duplicate.mp3}
    │       │       └── xml/network_security_config.xml
    │       └── androidTest/assets/sample_sheets/
```

---

## 8. Master Prompt สำหรับ AntiGravity IDE (แบ่ง 6 เฟส)

```markdown
### MASTER PROMPT (v6): PRODUCTION-GRADE HYBRID OMR EXAM SYSTEM

## GLOBAL CONTEXT (Applies strictly to every phase)
Architecture: Pure Native Android (Kotlin only) OMR scanner + Static Web Console (GitHub Pages) + Google Apps Script backend integrated with Google Sheets.
Repository: Follow docs/architecture.md Section 7 structure precisely.
Security & Auth:
- Apps Script is deployed "Execute as: Me / Who has access: Only myself". No shared secret, no HMAC in client code.
- Android sends `Authorization: Bearer <Google_OAuth_Access_Token>` using Google Authorization API.
- Web Console operates purely static on GitHub Pages, using Google Identity Services (GIS) token client to read sheets via Google Sheets API (read-only in memory).
Data Principles:
- Backend persists scans append-only into `Raw_Scans` with UUID `scan_id` deduplication.
- Auto-provision multi-room tabs (`Room_XXX`) and `Index` overview using dynamic formulas.
- Android uses two-stage geometry correction: Global 4-corner homography + local row-by-row interpolation using dual-sided timing tracks P_i(t) = (1-t)P_L,i + t*P_R,i.
Constraints: NO iOS, NO Flutter, NO WebViews, NO code stubs, NO `// TODO` placeholders. Provide complete, compilable implementations.

--------------------------------------------------------------------------------
### PHASE 1 — Cloud Backend & Sheet Provisioning (gas-backend)
Files:
- gas-backend/Code.js
- gas-backend/appsscript.json
- gas-backend/README.md

Requirements:
1. Implement the complete `Code.js` from Section 6 of architecture specification v6.
2. Ensure `loadScanIds_` uses memory-optimized bounded window queries (last 5,000 rows).
3. Ensure `provisionMultiRoomViews_` builds `Index`, auto-detects classes from `Students`, creates `Room_XXX` tabs, and maps dynamic formulas pulling the latest scan per student.
4. Set `appsscript.json` with `executeAs: USER_DEPLOYING` and `access: MYSELF`.
5. Write `README.md` documenting deployment steps and `curl` tests with `--post302` and Bearer token simulation.

Acceptance Criteria:
- Passing unit tests simulating `doPost` with: (a) valid batch insertion, (b) deduplicated batch via `scan_id`, (c) automatic generation of `Index` and `Room_XXX` tabs with formulas, (d) rejection of bad format/unknown exam.

--------------------------------------------------------------------------------
### PHASE 2 — Web Template Generator & PDF Layout Designer
Files:
- web-console/template-generator.html
- web-console/js/pdf-generator.js
- web-console/js/config.js
- web-console/css/style.css

Requirements:
1. Render print-ready A4 PDF using `jsPDF` at 100% scale without browser margin clipping.
2. Draw 4 corner fiducial markers: Top-left at 14x14 mm, others at 8x8 mm.
3. Draw Dual-Sided Timing Tracks: Left bars (5x1.6 mm) and Right bars (3x1.6 mm) positioned precisely at the vertical mid-line of each question row.
4. Draw standard 5-digit student ID bubble grid and 20/40/60 question rows (4 or 5 choices). Bubble borders set to neutral gray `#666666`.
5. Export coordinate metadata JSON matching the schema for `TemplateValidator.kt`.

Acceptance Criteria:
- Exported PDF prints with fiducial markers measuring precisely 14x14 mm and 8x8 mm.
- Coordinates JSON validates cleanly against client-side schema validator.

--------------------------------------------------------------------------------
### PHASE 3 — Android Curve-Proof OMR Engine
Files:
- android-scanner/app/src/main/java/com/school/omrscanner/omr/MarkerDetector.kt
- android-scanner/app/src/main/java/com/school/omrscanner/omr/TimingTrackDetector.kt
- android-scanner/app/src/main/java/com/school/omrscanner/omr/ScanlineInterpolator.kt
- android-scanner/app/src/main/java/com/school/omrscanner/omr/BubbleEvaluator.kt
- android-scanner/app/src/main/java/com/school/omrscanner/omr/AnswerEncoder.kt
- android-scanner/app/src/main/java/com/school/omrscanner/template/TemplateModel.kt
- android-scanner/app/src/main/java/com/school/omrscanner/template/TemplateValidator.kt

Requirements:
1. Implement OpenCV-based adaptive thresholding and contour hierarchy filtering.
2. Detect 14x14 mm top-left marker and automatically rotate image if rotated 90/180/270 degrees.
3. Compute perspective transform to standard 1000x1414 px.
4. Detect Left and Right Timing Tracks. Compute centroids P_L,i and P_R,i per row.
5. Implement ScanlineInterpolator evaluating bubble sampling centers along P_i(t) = (1-t)P_L,i + t*P_R,i.
6. Evaluate Bubble fill ratios: BLANK (<0.20), MULTIPLE (>=0.35, delta <0.25), VALID (>=0.45 or delta >=0.25), UNCERTAIN (flag '?').
7. Implement 5-digit student ID grid evaluator requiring exactly 1 valid mark per column.

Acceptance Criteria:
- Instrumented tests against sample images in `androidTest/assets/sample_sheets/` passing on: normal, 180-degree flipped, cylindrical curved page (comparing accuracy against non-interpolated grid), and faint erasure.

--------------------------------------------------------------------------------
### PHASE 4 — Android Persistence, Authentication & Network Sync
Files:
- android-scanner/app/src/main/java/com/school/omrscanner/data/local/{AppDatabase.kt, ScanDao.kt, ScanEntity.kt}
- android-scanner/app/src/main/java/com/school/omrscanner/auth/GoogleAuthProvider.kt
- android-scanner/app/src/main/java/com/school/omrscanner/sync/{OkHttpProvider.kt, GasApiService.kt, SyncWorker.kt}
- android-scanner/app/src/main/java/com/school/omrscanner/MainActivity.kt
- android-scanner/app/src/main/java/com/school/omrscanner/CameraActivity.kt
- android-scanner/app/src/main/java/com/school/omrscanner/ReviewActivity.kt
- android-scanner/app/src/main/AndroidManifest.xml
- android-scanner/app/src/main/res/xml/network_security_config.xml

Requirements:
1. Room SQLite persistence for scans with sync_status (0: pending, 1: synced, 2: rejected, 3: review_needed). `allowBackup=false`.
2. Implement `OkHttpProvider.kt` with a custom interceptor that handles HTTP 302 Found without stripping the `Authorization: Bearer <token>` header across redirects.
3. `GoogleAuthProvider.kt` using Google Authorization API to silently fetch and cache access tokens.
4. `SyncWorker.kt` running under WorkManager with `NetworkType.CONNECTED` and jittered exponential backoff (batches <= 50).
5. `ReviewActivity.kt` providing interactive confirmation for rows flagged with `?` or invalid student IDs.

Acceptance Criteria:
- End-to-end mock test verifying that a 302 redirect preserves Bearer tokens and successfully transitions a Room entity from `sync_status = 0` to `1`.

--------------------------------------------------------------------------------
### PHASE 5 — Web Console Analytics Dashboard
Files:
- web-console/index.html
- web-console/js/auth.js
- web-console/js/sheets-client.js
- web-console/js/analytics.js
- web-console/js/app.js

Requirements:
1. Google Identity Services (GIS) Sign-In with memory-only token storage.
2. Read `Raw_Scans` and `Students` via Google Sheets API v4.
3. Group scans by `(ExamID, StudentCode)` and extract the latest record by `ScannedAt`.
4. Calculate Item Analysis in JavaScript:
   - Item Difficulty: p = R / N
   - Item Discrimination: D = (R_upper - R_lower) / n (using top and bottom 27%)
   - Reliability Coefficient: KR-20 = [k / (k - 1)] * [1 - sum(p * q) / var_total]
5. Render color-coded question heatmap and export analysis report. All sheet data rendered via `textContent` under a strict Content Security Policy (CSP).

Acceptance Criteria:
- Unit tests validating item statistics calculation against verified benchmark datasets (including handling variance = 0 without dividing by zero).

--------------------------------------------------------------------------------
### PHASE 6 — Hardening, Security Audit & CI/CD
Files:
- .github/workflows/deploy.yml
- docs/architecture.md
- android-scanner/app/proguard-rules.pro

Requirements:
1. Write GitHub Actions workflow to build and deploy `web-console/` to GitHub Pages with SRI hashes.
2. Verify Android configuration: R8 minify enabled, `usesCleartextTraffic=false`, all activities non-exported except launcher.
3. Final verification of threat model checklist and operational manual.

Acceptance Criteria:
- Clean build of Android release APK with R8 and green deployment workflow run for GitHub Pages.
```