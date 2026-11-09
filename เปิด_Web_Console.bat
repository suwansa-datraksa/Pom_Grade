@echo off
chcp 65001 > nul
title Pom Grade Web Console Server
echo ===================================================
echo  Pom Grade Web Console - Local Server
echo  จัดทำโดย ครูสุวรรณษา เดชรักษา
echo ===================================================
echo.
echo กำลังเปิดเบราว์เซอร์ไปที่ http://localhost:8080/web-console/index.html ...
echo.
echo * หมายเหตุ: อย่าเพิ่งปิดหน้าต่างดำนี้ขณะใช้งาน Web Console
echo.
start http://localhost:8080/web-console/index.html
node server.js
pause
