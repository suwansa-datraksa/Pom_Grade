# คู่มือขั้นตอนที่ 2: การขอ OAuth 2.0 Client ID จาก Google Cloud Console

> **Pom Grade — ระบบตรวจข้อสอบ OMR ไฮบริด (Architecture Specification v6)**  
> **จัดทำโดย ครูสุวรรณษา เดชรักษา**

---

เอกสารนี้อธิบายขั้นตอนการสร้างและขอ **OAuth 2.0 Client ID** บน Google Cloud Console เพื่อให้:
1. **Web Console (บน GitHub Pages):** สามารถล็อกอินผ่าน Google Identity Services (GIS) และอ่านผลคะแนนจาก Google Sheets API v4 ในหน่วยความจำ (Read-only) โดยไม่ต้องมีเซิร์ฟเวอร์คนกลาง
2. **Android Scanner App:** สามารถขอ Access Token ของบัญชีครูแบบเงียบ (Silent Sign-In) เพื่อนำไปแนบกับคำขอ `Authorization: Bearer <token>` ส่งขึ้น Google Apps Script ได้โดยไม่ต้องฝัง Secret Key ในตัวแอป

---

## สรุปประเภท Credentials ที่ต้องสร้าง
| รายการ | ประเภท Application Type | จุดประสงค์การใช้งาน | ค่าที่ต้องใช้ในการสร้าง |
| :--- | :--- | :--- | :--- |
| **ตัวที่ 1** | **Web application** | สำหรับหน้าเว็บแดชบอร์ดบน GitHub Pages | URL ของ GitHub Pages (`https://...github.io`) |
| **ตัวที่ 2** | **Android** | สำหรับแอปสแกนข้อสอบมือถือ Android | Package Name (`com.school.omrscanner`) + ลายนิ้วมือใบรับรอง (SHA-1) |

---

## ขั้นตอนที่ 2.1: สร้างโปรเจกต์และเปิดใช้งาน Google Sheets API

1. เข้าไปยัง [Google Cloud Console](https://console.cloud.google.com/) แล้วล็อกอินด้วยบัญชี Google ที่สร้างชีตตรวจข้อสอบ
2. **สร้างโปรเจกต์ใหม่:**
   * คลิกที่เมนูดรอปดาวน์เลือกโปรเจกต์ (มุมบนซ้ายข้างโลโก้ Google Cloud) $\to$ คลิก **New Project**
   * **Project name:** ตั้งชื่อ เช่น `Pom-Grade-OMR`
   * **Location:** เลือกตามค่าเริ่มต้น (No organization หรือตามโดเมนโรงเรียน)
   * คลิกปุ่ม **CREATE**
   * รอสักครู่แล้วสลับไปเลือกโปรเจกต์ `Pom-Grade-OMR` ที่เพิ่งสร้างขึ้น
3. **เปิดใช้งาน Google Sheets API:**
   * ที่เมนูนำทางด้านซ้าย คลิก **APIs & Services** $\to$ เลือก **Library**
   * ในช่องค้นหา พิมพ์ว่า `Google Sheets API`
   * คลิกที่การ์ด **Google Sheets API** $\to$ กดปุ่ม **ENABLE** สีน้ำเงิน

---

## ขั้นตอนที่ 2.2: ตั้งค่าหน้าจอขอความยินยอม (OAuth consent screen)

ก่อนจะสร้าง Client ID ได้ Google บังคับให้กำหนดข้อมูลของแอพก่อน 1 ครั้ง:

1. ที่เมนูซ้ายมือ คลิก **APIs & Services** $\to$ **OAuth consent screen**
2. เลือกประเภทผู้ใช้ (**User Type**):
   * **Internal (ภายใน):** เลือกตัวนี้หากใช้อีเมล Google Workspace ของโรงเรียน (สะดวกที่สุด บุคคลในโรงเรียนล็อกอินได้ทันที)
   * **External (ภายนอก):** เลือกตัวนี้หากใช้อีเมลส่วนตัวทั่วไป (`@gmail.com`)
   * คลิกปุ่ม **CREATE**
3. **กรอกข้อมูลแอป (App information):**
   * **App name:** `Pom Grade`
   * **User support email:** เลือกอีเมลของคุณครู
   * **Developer contact information (Email addresses):** กรอกอีเมลของคุณครู
   * คลิก **SAVE AND CONTINUE**
4. **กำหนดขอบเขตสิทธิ์ (Scopes):**
   * คลิกปุ่ม **ADD OR REMOVE SCOPES**
   * ค้นหาและติ๊กเลือก:
     * `.../auth/spreadsheets.readonly` (ดูข้อมูลสเปรดชีต Google Sheets)
     * `.../auth/userinfo.email`
     * `openid`
   * คลิกปุ่ม **UPDATE** ด้านล่าง $\to$ คลิก **SAVE AND CONTINUE**
5. **ระบุบัญชีผู้ใช้ทดสอบ (Test users - เฉพาะกรณีเลือก External):**
   * คลิก **+ ADD USERS**
   * พิมพ์อีเมล Google ของคุณครูที่จะใช้ล็อกอิน
   * คลิก **ADD** $\to$ คลิก **SAVE AND CONTINUE**
6. คลิก **BACK TO DASHBOARD**

---

## ขั้นตอนที่ 2.3: ขอ Client ID ตัวที่ 1 สำหรับ Web Console (GitHub Pages)

1. ที่เมนูด้านซ้าย คลิก **APIs & Services** $\to$ **Credentials**
2. ด้านบน คลิกปุ่ม **+ CREATE CREDENTIALS** $\to$ เลือก **OAuth client ID**
3. กำหนดค่าดังนี้:
   * **Application type:** เลือก **Web application**
   * **Name:** พิมพ์ `Pom Grade Web Console`
   * เลื่อนลงมาที่หัวข้อ **Authorized JavaScript origins (ต้นทาง JavaScript ที่ได้รับอนุญาต):**
     * คลิก **+ ADD URI**
     * ใส่ URL ของเว็บ GitHub Pages ของคุณครู เช่น:
       ```text
       https://<username>.github.io
       ```
     * *(แนะนำ)* เพิ่ม URL สำหรับทดสอบในเครื่องคอมพิวเตอร์ด้วย:
       ```text
       http://localhost
       http://127.0.0.1:8080
       ```
4. คลิกปุ่ม **CREATE** ด้านล่าง
5. หน้าจอจะแสดงกล่องป๊อปอัป **OAuth client created**:
   * ให้คัดลอกค่า **Client ID** (ข้อความยาวๆ ที่ลงท้ายด้วย `.apps.googleusercontent.com`) เก็บไว้
   *(ตัวอย่าง: `123456789012-abcdefghijklmnopqrstuvwxyz.apps.googleusercontent.com`)*

---

## ขั้นตอนที่ 2.4: ขอ Client ID ตัวที่ 2 สำหรับแอปมือถือ Android (Android App)

### 2.4.1 ค่าลายนิ้วมือใบรับรอง (SHA-1 Fingerprint) ของเครื่องคอมพิวเตอร์นี้

ระบบได้ทำการตรวจสอบและออกใบรับรอง Debug Keystore ให้เครื่องของคุณครูเรียบร้อยแล้ว ได้ค่า **SHA-1** ที่แน่นอนคือ:
```text
70:B2:4E:62:CC:BC:3A:F5:AC:91:50:75:BC:99:D3:A3:8F:25:9C:C0
```
> คุณครูสามารถคัดลอกค่า `70:B2:4E:62:CC:BC:3A:F5:AC:91:50:75:BC:99:D3:A3:8F:25:9C:C0` ไปวางในช่อง **SHA-1 certificate fingerprint** ใน Google Cloud Console ได้ทันที!

*(หากต้องการตรวจสอบด้วยตนเองอีกครั้ง สามารถใช้คำสั่ง: `& 'C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe' -list -v -keystore "$env:USERPROFILE\.android\debug.keystore" -alias androiddebugkey -storepass android -keypass android`)*

### 2.4.2 สร้าง Android Client ID ใน Google Cloud Console
1. กลับมาที่หน้า **Credentials** ใน Google Cloud Console
2. คลิก **+ CREATE CREDENTIALS** $\to$ เลือก **OAuth client ID**
3. กำหนดค่าดังนี้:
   * **Application type:** เลือก **Android**
   * **Name:** พิมพ์ `Pom Grade Android Scanner`
   * **Package name:** ต้องระบุให้ตรงกับโปรเจกต์ คือ:
     ```text
     com.school.omrscanner
     ```
   * **SHA-1 certificate fingerprint:** นำค่า SHA-1 ที่คัดลอกได้จากคำสั่งด้านบนมาวางลงในช่องนี้
4. คลิกปุ่ม **CREATE**

---

## ขั้นตอนที่ 2.5: การนำ Client ID ไปใส่ในระบบ Pom Grade

### 1. นำไปใส่ใน Web Console (เลือกวิธีที่สะดวกได้เลยครับ):

#### 🔹 วิธีที่ 1: กรอกผ่านหน้าเว็บโดยตรง (สะดวกที่สุด ไม่ต้องแตะโค้ด)
1. เปิดไฟล์ `web-console/index.html` ด้วยโปรแกรมเว็บเบราว์เซอร์ (Google Chrome, Microsoft Edge) หรือเปิดผ่านลิงก์ GitHub Pages ของคุณครู:
   ```text
   https://suwansa-datraksa.github.io/Pom_Grade/web-console/index.html
   ```
2. บริเวณด้านบนใต้หัวข้อเว็บ จะมีกล่องแถบสีน้ำเงินเขียนว่า:
   > 🔐 **การเข้าถึงข้อมูลแบบ Read-Only ด้วย Google Identity Services**
3. ในกล่องนั้นจะมีช่องให้กรอกข้อความ:
   * **ช่องข้อความ:** `ระบุ Google OAuth 2.0 Client ID (หรือใช้ค่าเริ่มต้นที่ตั้งไว้)`
   * นำค่า **Web Client ID** ที่ได้จาก Google Cloud Console (เช่น `xxxxxxxxxxxx-xxxxxxxx.apps.googleusercontent.com`) วางลงในช่องนี้
4. คลิกปุ่ม **"บันทึก & เข้าสู่ระบบ"**
5. หน้าต่างป๊อปอัปของ Google จะเปิดขึ้นมา ให้คุณครูเลือกบัญชี Google $\to$ กดยินยอม $\to$ หน้าเว็บจะดึงผลคะแนนและสถิติจาก Google Sheets มาแสดงทันที
   *(ระบบจะจดจำค่านี้ไว้ในเบราว์เซอร์อัตโนมัติ ครั้งต่อไปไม่ต้องกรอกซ้ำ)*

---

#### 🔹 วิธีที่ 2: แก้ไขในไฟล์โค้ด (ถาวร เหมาะสำหรับขึ้น GitHub Pages)
วิธีนี้จะทำให้ Web Console มี Client ID ฝังเป็นค่าเริ่มต้นอัตโนมัติ ทุกคนที่เข้าเว็บจะไม่ต้องกรอก Client ID เองอีกต่อไป:
1. เปิดไฟล์ [web-console/js/app.js](file:///e:/Pom/Code/Pom%20Grade/web-console/js/app.js) ด้วยโปรแกรมแก้ไขข้อความหรือ VS Code
2. ดูที่บรรทัดที่ 14 ในส่วนการตั้งค่า `CONFIG`:
   ```javascript
   const CONFIG = {
     SPREADSHEET_ID: '1KcL20IBw5r36ODy9lcsBal6SmeZ9rRitctotZfwJKH4',
     DEFAULT_CLIENT_ID: '407408718192.apps.googleusercontent.com', // <--- แก้ไขบรรทัดนี้
     PASSING_PERCENT: 50.0,
     CREDIT: 'จัดทำโดย ครูสุวรรณษา เดชรักษา'
   };
   ```
3. เปลี่ยนข้อความในเครื่องหมายคำพูดเดี่ยว `'...'` หลัง `DEFAULT_CLIENT_ID:` เป็น Client ID ของคุณครู เช่น:
   ```javascript
   DEFAULT_CLIENT_ID: '123456789012-abcdefghijklmnopqrstuvwxyz.apps.googleusercontent.com',
   ```
4. บันทึกไฟล์ (Ctrl + S) แล้ว Push ขึ้น GitHub ได้ทันที

---

#### 🔹 วิธีที่ 3: ให้ผู้ช่วย AI ใส่ให้ทันที
* คุณครูเพียงคัดลอกค่า **Web Client ID** ที่ได้จาก Google Cloud Console ส่งเข้ามาในแชทนี้
* ผู้ช่วย AI จะนำไปใส่ลงในไฟล์โค้ดและทดสอบความถูกต้องให้โดยอัตโนมัติครับ

### 2. นำไปใส่ใน Android Scanner App:
* สำหรับ Android คุณครู**ไม่ต้องแก้ไขโค้ดใดๆ** เนื่องจาก Google Play Services จะตรวจสอบชื่อ Package `com.school.omrscanner` คู่กับลายเซ็นใบรับรอง SHA-1 ของไฟล์ APK ในคลาวด์ให้อัตโนมัติเมื่อกดปุ่มลงชื่อเข้าใช้ในแอปมือถือ

---

> **เสร็จสิ้นขั้นตอนที่ 2** ระบบความปลอดภัย OAuth 2.0 พร้อมสำหรับการยืนยันตัวตนแล้วครับ!

