@echo off
REM Pom Grade — Master Test Runner Batch Script
REM จัดทำโดย ครูสุวรรณษา เดชรักษา

echo =================================================================
echo  POM GRADE — MASTER TEST SUITE
echo  จัดทำโดย ครูสุวรรณษา เดชรักษา
echo =================================================================

where node >nul 2>nul
if %errorlevel% neq 0 (
    if exist "C:\Program Files\nodejs\node.exe" (
        "C:\Program Files\nodejs\node.exe" run_all_tests.js
    ) else (
        echo [ERROR] Node.js is not found in PATH or standard installation directory.
        pause
        exit /b 1
    )
) else (
    node run_all_tests.js
)

pause
