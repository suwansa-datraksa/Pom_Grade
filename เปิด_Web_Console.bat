@echo off
cd /d "E:\Pom\Code\Pom Grade"
echo ===================================================
echo  Starting Pom Grade Web Console Server...
echo  http://localhost:8080/web-console/index.html
echo ===================================================
echo.
start http://localhost:8080/web-console/index.html
node server.js
pause
