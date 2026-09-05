@echo off
rem ============================================================
rem  Firewall rule helper for dual-device sync (LAN / Wi-Fi).
rem  Adds inbound allow rules for:
rem    TCP 8765 (sync service, change PORT below if needed)
rem    UDP 9112 (desktop heartbeat broadcast)
rem  Self-elevates to Administrator (UAC prompt will appear).
rem ============================================================
setlocal
set PORT=8765

net session >nul 2>nul
if errorlevel 1 (
    echo Requesting administrator privileges...
    powershell -NoProfile -Command "Start-Process -FilePath '%~f0' -Verb RunAs"
    exit /b
)

echo Adding firewall rule: TCP %PORT% (sync service)
netsh advfirewall firewall delete rule name="SMTY-Sync-TCP" >nul 2>nul
netsh advfirewall firewall add rule name="SMTY-Sync-TCP" dir=in action=allow protocol=TCP localport=%PORT%

echo Adding firewall rule: UDP 9112 (heartbeat broadcast)
netsh advfirewall firewall delete rule name="SMTY-Sync-UDP" >nul 2>nul
netsh advfirewall firewall add rule name="SMTY-Sync-UDP" dir=in action=allow protocol=UDP localport=9112

echo.
echo [OK] Firewall rules added. LAN (same Wi-Fi) sync should work now.
echo If the sync port was changed in the app, re-run with the same port
echo or edit PORT at the top of this file.
pause
