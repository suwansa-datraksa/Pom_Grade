# คู่มือขั้นตอนที่ 4: การ Push โค้ดขึ้น GitHub เพื่อเปิดใช้งาน GitHub Pages

> **Pom Grade — ระบบตรวจข้อสอบ OMR ไฮบริด (Architecture Specification v6)**  
> **จัดทำโดย ครูสุวรรณษา เดชรักษา**

---

เอกสารนี้อธิบายขั้นตอนการนำซอร์สโค้ดโปรเจกต์ **Pom Grade** ทั้งหมดขึ้นสู่ **GitHub** และเปิดใช้งาน **GitHub Pages** ผ่านระบบอัตโนมัติ (GitHub Actions CI/CD) เพื่อให้หน้าเว็บคอนโซลสำหรับคุณครูสามารถเข้าถึงและใช้งานได้จากทุกที่ผ่านอินเทอร์เน็ตโดยไม่มีค่าใช้จ่ายและปลอดภัยสูงสุด

---

## 1. การทำงานของระบบ Deploy อัตโนมัติในโปรเจกต์
ในโปรเจกต์ Pom Grade มีไฟล์เวิร์กโฟลว์ CI/CD อยู่ที่ [`.github/workflows/deploy.yml`](file:///e:/Pom/Code/Pom%20Grade/.github/workflows/deploy.yml) ซึ่งจะทำงานอัตโนมัติทุกครั้งที่คุณครูกด Push โค้ดขึ้น GitHub:
1. **Job 1 (Test & Audit):** รันชุดทดสอบครบทั้ง 30 ข้อ (GAS Backend, PDF Generator, Android Sync, Psychometric Item Analysis) และตรวจสอบความปลอดภัยว่าไม่มี Secret Key ฝังอยู่ในโค้ด
2. **Job 2 (Deploy):** นำโฟลเดอร์ `web-console/` ขึ้นสู่ GitHub Pages พร้อมไฟล์ `.nojekyll`

---

## 2. ขั้นตอนการสร้าง Repository บน GitHub

1. เข้าไปที่เว็บไซต์ [GitHub](https://github.com/) และลงชื่อเข้าใช้บัญชีของคุณครู
2. คลิกที่เครื่องหมายบวก **`+`** (มุมบนขวา) $\to$ เลือก **New repository**
3. กำหนดค่าดังนี้:
   * **Repository name:** ตั้งชื่อ เช่น `pom-grade` หรือ `pom-grade-omr`
   * **Description:** `Production-Grade Hybrid OMR Exam System (Pom Grade)`
   * **Visibility:** เลือก **Public** (เพื่อให้ใช้ GitHub Pages ฟรีได้)
   * ⚠️ **ข้อสำคัญ:** ในส่วน *"Initialize this repository with:"* **ไม่ต้องติ๊กเลือก** Add a README file, .gitignore หรือ license (ปล่อยว่างทั้งหมด เพราะในเครื่องมีไฟล์เหล่านี้พร้อมแล้ว)
4. คลิกปุ่มสีเขียว **Create repository**
5. หน้าจอจะแสดง URL ของ Repository ที่สร้างขึ้น เช่น:
   ```text
   https://github.com/<ชื่อผู้ใช้-github>/pom-grade.git
   ```

---

## 3. ขั้นตอนการ Push โค้ดจากเครื่องคอมพิวเตอร์ขึ้น GitHub

เปิด PowerShell ในโฟลเดอร์โปรเจกต์ (`E:\Pom\Code\Pom Grade`) แล้วรันคำสั่งตามลำดับดังนี้:

### 3.1 ตรวจสอบสถานะ Git ในเครื่อง
```powershell
git status
```
*(ระบบควรแจ้งว่า `nothing to commit, working tree clean`)*

### 3.2 เชื่อมโยงโปรเจกต์เข้ากับ GitHub Remote
ให้เปลี่ยน `<YOUR_USERNAME>` และ `<REPO_NAME>` เป็นข้อมูลของบัญชี GitHub ของคุณครู:

```powershell
git remote add origin https://github.com/<YOUR_USERNAME>/<REPO_NAME>.git
```

> *หากเคยผูก remote ไว้แล้วและต้องการเปลี่ยน ให้ใช้คำสั่ง:*  
> `git remote set-url origin https://github.com/<YOUR_USERNAME>/<REPO_NAME>.git`

### 3.3 ตรวจสอบชื่อ Branch หลัก
ตรวจสอบว่าใช้ชื่อ branch `master` หรือ `main`:
```powershell
git branch -M master
```

### 3.4 Push ซอร์สโค้ดขึ้นสู่ GitHub
```powershell
git push -u origin master
```
*(หากเป็นการ push ครั้งแรก ระบบอาจเปิดหน้าต่างเบราว์เซอร์ให้กดยืนยันการล็อกอินบัญชี GitHub)*

---

## 4. การเปิดใช้งาน GitHub Pages ด้วย GitHub Actions

หลังจาก push โค้ดขึ้นไปแล้ว ให้ตั้งค่าเปิด GitHub Pages ดังนี้:

1. บนหน้าเว็บ GitHub Repository ของคุณครู คลิกแท็บ **Settings** (รูปเฟืองด้านบน)
2. ที่เมนูด้านซ้าย ในหมวด *Code and automation* คลิกเลือก **Pages**
3. ในส่วน **Build and deployment**:
   * ที่หัวข้อ **Source:** ให้คลิกดรอปดาวน์เปลี่ยนจาก *Deploy from a branch* เป็น:
     👉 **GitHub Actions**
4. การตั้งค่าจะถูกบันทึกทันทีโดยไม่ต้องกดปุ่ม Save

---

## 5. การตรวจสอบสถานะการ Deploy และเข้าใช้งานเว็บ

1. คลิกที่แท็บ **Actions** ด้านบนของหน้า Repository
2. คุณครูจะเห็นเวิร์กโฟลว์ชื่อ **`Deploy Pom Grade Web Console to GitHub Pages`** กำลังทำงาน
3. รอประมาณ 1–2 นาที จนกระทั่งเวิร์กโฟลว์ขึ้นเครื่องหมายถูกสีเขียว (Green Checkmark `✔`) ทั้งสอง Job:
   * `Run Automated Test Suites & Security Audit`
   * `Build & Deploy to GitHub Pages`
4. คลิกเข้าไปดูรายละเอียด จะมีลิงก์ URL ของเว็บไซต์แสดงอยู่ในส่วนการ Deploy เช่น:
   ```text
   https://<YOUR_USERNAME>.github.io/<REPO_NAME>/
   ```

---

## 6. ลิงก์หน้าเว็บสำหรับใช้งานในระบบ Pom Grade

เมื่อเปิดใช้งาน GitHub Pages เรียบร้อย คุณครูสามารถเข้าใช้งานได้ทันทีจากทุกอุปกรณ์:

* **หน้าแดชบอร์ดหลัก (KPI, Index, Item Analysis, Heatmap):**  
  `https://<YOUR_USERNAME>.github.io/<REPO_NAME>/`
* **หน้าสร้างและพิมพ์กระดาษคำตอบ A4 PDF:**  
  `https://<YOUR_USERNAME>.github.io/<REPO_NAME>/template-generator.html`

> [!IMPORTANT]
> **อย่าลืม:** นำ URL ของ GitHub Pages (`https://<YOUR_USERNAME>.github.io`) ไปใส่ในช่อง **Authorized JavaScript origins** ของ Web Client ID ใน Google Cloud Console (ตามคู่มือขั้นตอนที่ 2 ข้อ 2.3) เพื่อให้หน้าเว็บได้รับอนุญาตให้ล็อกอิน Google Identity Services ได้อย่างราบรื่นครับ

---

> **เสร็จสิ้นขั้นตอนที่ 4** เว็บคอนโซล Pom Grade ออนไลน์พร้อมใช้งานเรียบร้อยแล้วครับ!

