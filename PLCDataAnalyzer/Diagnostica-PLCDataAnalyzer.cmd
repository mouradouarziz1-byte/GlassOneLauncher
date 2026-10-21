@echo off
setlocal
chcp 65001 >nul
set "LOG=%TEMP%\PLCDataAnalyzer-diagnostica.txt"
(
  echo PLC Data Analyzer - Diagnostica
  echo Data: %DATE% %TIME%
  echo.
  echo === Windows ===
  ver
  echo Architecture: %PROCESSOR_ARCHITECTURE%
  echo User: %USERNAME%
  echo.
  echo === Gruppo Siemens TIA Openness ===
  whoami /groups | findstr /I /C:"Siemens TIA Openness"
  echo.
  echo === Registro TIA Openness V21 ===
  reg query "HKLM\SOFTWARE\Siemens\Automation\Openness\21.0\PublicAPI" /s
  echo.
  echo === EXE ===
  dir "%~dp0PLCDataAnalyzer.exe"
) > "%LOG%" 2>&1
start notepad "%LOG%"
endlocal
