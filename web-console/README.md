# Static Web Console (`web-console`)

> **ระบบบริหารจัดการและแดชบอร์ดติดตามผลการสอบ OMR (Architecture v6)**  
> **Static Web Application บน GitHub Pages พร้อม Google Identity Services (GIS)**  
> **จัดทำโดย ครูสุวรรณษา เดชรักษา**

---

## 1. ภาพรวมระบบ (System Overview)
เว็บคอนโซลกลางสำหรับการติดตาม ตรวจสอบ และวิเคราะห์ผลการสอบ OMR แบบ Real-time พัฒนาในรูปแบบ **Pure Static Single Page Application (SPA)**:
- **โฮสต์แบบไม่มีเซิร์ฟเวอร์ (GitHub Pages):** ทำงานโดยตรงบนเว็บเบราว์เซอร์ของผู้ใช้งาน ไม่ต้องมีเซิร์ฟเวอร์ Node.js/PHP/Python คนกลาง
- **การยืนยันตัวตนผ่าน Google Identity Services (GIS):** ขอสิทธิ์ OAuth 2.0 Read-Only Token ในหน่วยความจำ (`https://www.googleapis.com/auth/spreadsheets.readonly`)
- **การอ่านข้อมูลผ่าน Google Sheets API v4:** ดึงข้อมูลตรงจากแท็บ `Index` และ `Room_XXX` มาประมวลผลบนเครื่องไคลเอนต์
- **การวิเคราะห์ผลสอบแบบ Real-time:** คำนวณยอดผู้เข้าสอบ ขาดสอบ คะแนนเฉลี่ย สูงสุด ต่ำสุด และอัตราการสอบผ่าน พร้อมตัวกรองค้นหาตามห้องเรียน
- **ส่งออกข้อมูล (Export Data):** รองรับการดาวน์โหลดสรุปภาพรวมเป็นไฟล์ CSV (UTF-8 BOM รองรับภาษาไทยใน Microsoft Excel) และพิมพ์ใบคะแนนรายบุคคลสำหรับแต่ละห้องเรียน

---

## 2. โครงสร้างไฟล์
```
web-console/
├── index.html       # โครงสร้างหน้าเว็บหลักและ Modal แสดงใบคะแนน
├── style.css        # สไตล์ธีม Light Theme คอนทราสต์สูง อ่านง่าย สบายตา
├── app.js           # ตรรกะ GIS OAuth, Google Sheets API v4 In-Memory Fetcher, และตารางไดนามิก
└── README.md        # เอกสารขั้นตอนการติดตั้งและ Deploy บน GitHub Pages
```

---

## 3. ขั้นตอนการตั้งค่า Google Cloud Credentials

เพื่อให้เว็บคอนโซลสามารถอ่านข้อมูลจาก Google Sheets ได้โดยตรงผ่าน Google Identity Services:
1. ไปที่ [Google Cloud Console](https://console.cloud.google.com/)
2. สร้างหรือเลือกโปรเจกต์ Google Cloud
3. ไปที่ **APIs & Services** > **Library** และเปิดใช้งาน **Google Sheets API**
4. ไปที่ **APIs & Services** > **Credentials** > **Create Credentials** > **OAuth client ID**
5. เลือก Application type: **Web application**
6. ในส่วน **Authorized JavaScript origins** ให้เพิ่ม URL:
   - สำหรับทดสอบในเครื่อง: `http://localhost:8080` หรือ `http://127.0.0.1:5500`
   - สำหรับ GitHub Pages: `https://<your-username>.github.io`
7. คัดลอก **Client ID** (เช่น `xxxxxxxxxxxx.apps.googleusercontent.com`)
8. นำ Client ID ไปกรอกในช่องตั้งค่าบนหน้าเว็บคอนโซล หรือระบุไว้ใน `CONFIG.DEFAULT_CLIENT_ID` ในไฟล์ `app.js`

---

## 4. ขั้นตอนการ Deploy บน GitHub Pages

1. Push โค้ดทั้งหมดขึ้น GitHub Repository
2. ไปที่หน้า Repository บน GitHub > เมนู **Settings**
3. เลือกแถบเมนูด้านซ้าย **Pages**
4. ในส่วน **Build and deployment**:
   - Source: **Deploy from a branch**
   - Branch: เลือกกิ่ง `main` (หรือกิ่งที่ต้องการ) และเลือกโฟลเดอร์ `/web-console` หรือ root
5. กด **Save**
6. รอระบบ GitHub Actions ดำเนินการ Deploy (ประมาณ 1-2 นาที)
7. เข้าใช้งานได้ทันทีผ่าน URL: `https://<your-username>.github.io/<repo-name>/web-console/`

---

## 5. ความปลอดภัยและหลักการข้อมูล (Security Principles)
- **Zero Middle-Tier Storage:** ไม่มีการบันทึกโทเค็นหรือข้อมูลคะแนนสอบของนักเรียนลงในเซิร์ฟเวอร์ภายนอก ข้อมูลทั้งหมดประมวลผลในหน่วยความจำ RAM ของเว็บเบราว์เซอร์เท่านั้น
- **Read-Only Scopes:** สิทธิ์ที่ขอจากผู้ใช้จำกัดเฉพาะการอ่านชีต (`spreadsheets.readonly`) เท่านั้น ไม่สามารถแก้ไข ดัดแปลง หรือลบข้อมูลใดๆ ในสเปรดชีตได้

---
**จัดทำโดย ครูสุวรรณษา เดชรักษา**
