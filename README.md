# Production-Grade Hybrid OMR Exam System

> **Architecture Specification v6**  
> Pure Native Android (Kotlin only) OMR Scanner + Static Web Console (GitHub Pages) + Google Apps Script Cloud Backend  
> **จัดทำโดย ครูสุวรรณษา เดชรักษา**

---

## สรุปภาพรวมของโครงการ (Project Overview)
ระบบตรวจข้อสอบและประมวลผลกระดาษคำตอบ OMR ความแม่นยำสูงระดับอุตสาหกรรม ประกอบด้วย 3 องค์ประกอบหลักตามสถาปัตยกรรม Architecture v6:
1. **`android-app/`**: แอปพลิเคชัน Android แบบ Native แท้ (Kotlin) ประมวลผลภาพสดผ่าน CameraX + การปรับแก้มุมมองภาพ 2 ขั้นตอน (Stage 1: Global 4-Corner Homography + Stage 2: Dual-sided Timing Track Interpolation $\overset{\rightharpoonup}{P}_i(t) = (1-t)\overset{\rightharpoonup}{P}_{L,i} + t\overset{\rightharpoonup}{P}_{R,i}$) ตรวจจับการฝนคำตอบแบบ Adaptive Thresholding, แคชลง Room Database ในเครื่อง และซิงค์ขึ้น Google Apps Script อัตโนมัติด้วย OAuth 2.0 Bearer Token
2. **`web-console/`**: แดชบอร์ดสถิติและการจัดการแบบ Pure Static Web (HTML5/CSS3/Vanilla ES6+) ทำงานบน GitHub Pages เชื่อมต่อ Google Sheets API v4 โดยตรงในหน่วยความจำผ่าน Google Identity Services (GIS)
3. **`gas-backend/`**: คลาวด์แบ็กเอนด์บน Google Apps Script V8 ทำหน้าที่เป็น Ingestion Engine แบบ Append-only ลงชีต `Raw_Scans` ป้องกันข้อมูลซ้ำซ้อนด้วย Bounded Window Query (5,000 แถวล่าสุด) พร้อมระบบ Auto-provisioning สร้างแท็บ `Index` และแท็บห้องเรียน `Room_XXX` อัตโนมัติด้วยสูตรไดนามิก `XLOOKUP`

---

## โครงสร้างโปรเจกต์ (Repository Structure)
```
Pom Grade/
├── .gitignore
├── README.md
├── docs/
│   └── architecture.md               # เอกสารสถาปัตยกรรมระบบ v6 ครบถ้วนทุกส่วน
├── gas-backend/                      # Phase 1: Google Apps Script Backend
│   ├── Code.js
│   ├── appsscript.json
│   ├── README.md
│   └── tests/
│       └── gas_backend.test.js
├── android-app/                      # Phase 2: Pure Native Kotlin OMR Scanner
│   ├── build.gradle.kts
│   ├── settings.gradle.kts
│   ├── gradle.properties
│   ├── gradle/libs.versions.toml
│   ├── README.md
│   └── app/
│       ├── build.gradle.kts
│       ├── proguard-rules.pro
│       └── src/main/
│           ├── AndroidManifest.xml
│           ├── java/com/pomgrade/omr/
│           │   ├── OmrApplication.kt
│           │   ├── auth/GoogleAuthManager.kt
│           │   ├── data/
│           │   │   ├── local/ (AppDatabase, ScanDao, ScanEntity)
│           │   │   ├── remote/ (AuthInterceptor, GasApiService, GasScanPayload)
│           │   │   └── repository/ (ScanRepository)
│           │   ├── omr/ (BubbleAnalyzer, FiducialDetector, GeometryRectifier, OmrConfig, Point2D, TimingTrackInterpolator, OmrProcessor)
│           │   ├── sync/SyncWorker.kt
│           │   └── ui/ (MainActivity, ScannerActivity, HistoryActivity, SettingsActivity, ScannerOverlayView)
│           └── res/ (layouts, drawables, values, colors, strings, themes)
└── web-console/                      # Phase 3: Static Web Console (GitHub Pages)
    ├── index.html
    ├── style.css
    ├── app.js
    └── README.md
```

---

## แหล่งข้อมูล Google Spreadsheet
- **Spreadsheet ID:** `1KcL20IBw5r36ODy9lcsBal6SmeZ9rRitctotZfwJKH4`
- **Spreadsheet URL:** [https://docs.google.com/spreadsheets/d/1KcL20IBw5r36ODy9lcsBal6SmeZ9rRitctotZfwJKH4/edit](https://docs.google.com/spreadsheets/d/1KcL20IBw5r36ODy9lcsBal6SmeZ9rRitctotZfwJKH4/edit)

---

## สถานะการพัฒนาทุกลำดับขั้น (All Phases Complete)
- [x] **PHASE 1 — Cloud Backend & Sheet Provisioning (`gas-backend`)**
  - [x] `gas-backend/Code.js` (doPost, loadScanIds_ bounded window 5k rows, provisionMultiRoomViews_)
  - [x] `gas-backend/appsscript.json` (executeAs: USER_DEPLOYING, access: MYSELF)
  - [x] `gas-backend/README.md` (curl tests with `--post302` and Bearer token simulation)
  - [x] Automated Unit Test Suite (`gas-backend/tests/gas_backend.test.js` - 9/9 Passed)
- [x] **PHASE 2 — Pure Native Android OMR Scanner (`android-app`)**
  - [x] Pure Kotlin Native implementation (NO Flutter, NO iOS, NO WebViews)
  - [x] Two-stage geometry correction: Global 4-corner homography + local row-by-row interpolation using dual-sided timing tracks $\overset{\rightharpoonup}{P}_i(t) = (1-t)\overset{\rightharpoonup}{P}_{L,i} + t\overset{\rightharpoonup}{P}_{R,i}$
  - [x] CameraX Live Stream & `ScannerOverlayView` with real-time target feedback
  - [x] Room Database local persistence + UUIDv4 `scan_id` deduplication
  - [x] Google Play Services Auth + OAuth 2.0 Bearer Token client
  - [x] WorkManager background sync + OkHttp 302 redirect handler
- [x] **PHASE 3 — Static Web Console (`web-console`)**
  - [x] Pure static deployment on GitHub Pages
  - [x] Google Identity Services (GIS) token client (read-only in memory)
  - [x] Direct in-memory Google Sheets API v4 integration
  - [x] KPI metrics, dynamic room overview table, student scorecard modal, and CSV export
  - [x] Clean, modern Light Theme with high contrast and readable typography
  - [x] Prominent credit text: `จัดทำโดย ครูสุวรรณษา เดชรักษา`

---
**จัดทำโดย ครูสุวรรณษา เดชรักษา**
