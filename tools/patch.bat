@echo off
rem FarLands G1 patcher helper (Windows). 拖入 Minecraft 26.2 客户端 jar 即可。
setlocal
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0patch.ps1" %*
echo.
pause
