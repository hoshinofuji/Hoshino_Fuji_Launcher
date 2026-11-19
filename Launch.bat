@echo off
setlocal
cd /d "%~dp0"
where java >nul 2>nul
if errorlevel 1 (
  echo HFML requires Java 21 or newer. Install a trusted JDK and add it to PATH.
  pause
  exit /b 1
)
java -jar "%~dp0HFML.jar"
if errorlevel 1 (
  echo HFML could not start. Check Java version and the error above.
  pause
  exit /b 1
)
