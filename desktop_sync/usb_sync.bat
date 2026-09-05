@echo off
rem ============================================================
rem  USB sync channel helper (adb reverse)
rem  Run this while the phone is connected via USB and USB
rem  debugging is enabled. After it succeeds, set in the app:
rem     Settings -> Desktop Sync -> syncHost = 127.0.0.1
rem     syncPort = 8765 (same as the PC sync service port)
rem ============================================================
setlocal
set PORT=8765

where adb >nul 2>nul
if errorlevel 1 (
    echo [ERROR] adb not found in PATH. Install Android platform-tools first.
    pause
    exit /b 1
)

adb wait-for-device
adb reverse tcp:%PORT% tcp:%PORT%
if errorlevel 1 (
    echo [ERROR] adb reverse failed. Check: phone connected? USB debugging on?
    pause
    exit /b 1
)

echo.
echo [OK] USB sync channel ready: phone 127.0.0.1:%PORT% -^> PC port %PORT%
echo Start the PC sync service (Data Center - Dual-Device Sync - Start),
echo then tap "Sync Now" in the app.
echo.
echo Note: the channel lasts until the USB cable is unplugged. Re-run if needed.
pause
