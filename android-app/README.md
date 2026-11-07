# Native Android OMR Scanner (`android-app`)

> **ระบบตรวจข้อสอบ OMR ความแม่นยำสูงระดับอุตสาหกรรม (Architecture v6)**  
> **Pure Native Android (Kotlin only) — ไม่ใช้ iOS, Flutter หรือ WebView**  
> **จัดทำโดย ครูสุวรรณษา เดชรักษา**

---

## 1. ภาพรวมระบบ (System Overview)
แอปพลิเคชัน Android ระดับ Production-Grade สำหรับตรวจข้อสอบและวิเคราะห์กระดาษคำตอบ OMR แบบ Real-time ผ่านกล้อง:
- **กล้องและการประมวลผลภาพ (CameraX + Native Processing):** ทำงานแบบสตรีมสดผ่าน `ImageAnalysis` กลยุทธ์ `STRATEGY_KEEP_ONLY_LATEST`
- **การปรับแก้มุมมองภาพ 2 ขั้นตอน (Two-Stage Geometry Rectification):**
  1. *Stage 1 (Global 4-Corner Homography):* ตรวจจับจุดมาร์กเกอร์มุมทั้ง 4 จุด (Top-Left, Top-Right, Bottom-Right, Bottom-Left) และแปลงมุมมองภาพด้วยสมการ Perspective Transformation สู่ Canonical Coordinate Space ($W = 1200, H = 1700$)
  2. *Stage 2 (Local Dual-Sided Timing Track Interpolation):* ตรวจจับเส้นแทร็กจับเวลาทั้งฝั่งซ้ายและฝั่งขวาของแต่ละแถวข้อสอบ และชดเชยการบิดงอ/โค้งงอของกระดาษด้วยสมการ:
     $$\overset{\rightharpoonup}{P}_i(t) = (1 - t)\overset{\rightharpoonup}{P}_{L,i} + t \overset{\rightharpoonup}{P}_{R,i}$$
     โดยที่ $t \in [0, 1]$ คือตำแหน่งสัมพัทธ์ของตัวเลือกข้อสอบ (A, B, C, D)
- **การวิเคราะห์การฝนคำตอบ (BubbleAnalyzer):** วัดความหนาแน่นของเม็ดพิกเซลสีเข้มภายใน Mask วงกลมแบบปรับค่าตามแสงท้องถิ่น (Adaptive Thresholding) พร้อมตรวจสอบความไม่ชัดเจน (Ambiguous margin) ในกรณีที่ฝน 2 ข้อ
- **ฐานข้อมูลแคชในเครื่อง (Room Database):** จัดเก็บผลการสแกนแบบ Append-Only พร้อมสร้าง `scan_id` (UUIDv4) ป้องกันข้อมูลซ้ำซ้อนในระดับฐานข้อมูล
- **ระบบซิงค์ข้อมูลเบื้องหลัง (WorkManager + Retrofit + OkHttp):** ส่งผลสอบขึ้น Google Apps Script อัตโนมัติ พร้อมส่ง Header `Authorization: Bearer <Google_OAuth_Access_Token>` และรองรับการจัดการ HTTP 302 Redirect แบบคงสถานะ POST

---

## 2. โครงสร้างแพ็กเกจ (Package Structure)
```
android-app/app/src/main/java/com/pomgrade/omr/
├── OmrApplication.kt                 # Application Class เชื่อมโยง Dependency
├── auth/
│   └── GoogleAuthManager.kt          # จัดการ Google Sign-In & ขอ OAuth Bearer Token
├── data/
│   ├── local/
│   │   ├── AppDatabase.kt            # Room Database
│   │   ├── ScanDao.kt                # Data Access Object
│   │   └── ScanEntity.kt             # เอนทิตี Scan ในเครื่อง
│   ├── remote/
│   │   ├── AuthInterceptor.kt        # ดักจับและแนบ Bearer Token + จัดการ 302 Redirect
│   │   ├── GasApiService.kt          # Retrofit Service เชื่อมต่อ Google Apps Script
│   │   └── GasScanPayload.kt         # DTO สำหรับส่ง Batch Data
│   └── repository/
│       └── ScanRepository.kt         # Single Source of Truth และตรรกะการซิงค์
├── omr/
│   ├── BubbleAnalyzer.kt             # วิเคราะห์ค่าความเข้มของการฝนวงกลม
│   ├── FiducialDetector.kt           # ค้นหามาร์กเกอร์มุมทั้ง 4 จุด
│   ├── GeometryRectifier.kt          # Stage 1: Homography & Perspective Transform
│   ├── OmrConfig.kt                  # พิกัดและค่าคงที่ของแม่แบบกระดาษคำตอบ
│   ├── Point2D.kt                    # คลาสเวกเตอร์ 2 มิติรองรับคณิตศาสตร์เชิงเส้น
│   ├── TimingTrackInterpolator.kt    # Stage 2: Dual-sided Timing Track Interpolation
│   └── OmrProcessor.kt               # ไปป์ไลน์หลักเชื่อมโยง Stage 1 -> Stage 2 -> Bubble
├── sync/
│   └── SyncWorker.kt                 # WorkManager ประมวลผลซิงค์เบื้องหลัง
└── ui/
    ├── components/
    │   └── ScannerOverlayView.kt     # Custom View แสดงกรอบเล็งและไฮไลต์มาร์กเกอร์
    ├── HistoryActivity.kt            # หน้าประวัติการสแกนและสถานะซิงค์
    ├── MainActivity.kt               # หน้าแดชบอร์ดหลัก
    ├── ScannerActivity.kt            # หน้าจอสแกนข้อสอบแบบ Real-time
    └── SettingsActivity.kt           # หน้าตั้งค่าและเข้าสู่ระบบ Google Account
```

---

## 3. ขั้นตอนการคอมไพล์และติดตั้ง (Build & Run Guide)

### สิ่งที่จำเป็นต้องมี:
- **Android Studio Iguana | 2023.2.1** หรือใหม่กว่า
- **Android SDK:** Compile SDK 34, Min SDK 26 (Android 8.0 Oreo ขึ้นไป)
- **JDK:** OpenJDK 17

### คำสั่งคอมไพล์ผ่าน Gradle:
```bash
# คอมไพล์และสร้างไฟล์ APK สำหรับ Debug
./gradlew assembleDebug

# คอมไพล์และสร้างไฟล์ APK สำหรับ Release (พร้อม ProGuard / R8 Optimization)
./gradlew assembleRelease
```

---

## 4. ความปลอดภัยและการยืนยันตัวตน (Security Architecture)
- แอปพลิเคชันใช้ **Google Play Services Auth API** ในการขอสิทธิ์เข้าถึงบัญชี Google ของผู้ตรวจ
- ดึง OAuth 2.0 Bearer Token ที่มี Scope: `https://www.googleapis.com/auth/spreadsheets`
- ส่งผ่าน Header `Authorization: Bearer <TOKEN>` โดยตรงไปยัง Google Apps Script Web App
- **ไม่มีการเก็บ Secret Key, Password หรือ API Key ใดๆ ไว้ในโค้ดฝั่ง Android**

---
**จัดทำโดย ครูสุวรรณษา เดชรักษา**

