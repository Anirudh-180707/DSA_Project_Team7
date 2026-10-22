@echo off
REM Double-click to compile and launch the Cyclone Control Room GUI on Windows.
REM It recompiles every time, so any code change you save is picked up.
cd /d "%~dp0"

where javac >nul 2>nul
if errorlevel 1 (
  for /d %%i in ("C:\Program Files\Eclipse Adoptium\jdk-*") do set "PATH=%%i\bin;%PATH%"
)
where javac >nul 2>nul
if errorlevel 1 (
  echo Java JDK not found. Install Temurin 21 from adoptium.net, then try again.
  pause
  exit /b 1
)

if exist out rmdir /s /q out
dir /s /b src\*.java test\*.java > sources.txt
javac --release 11 -encoding UTF-8 -d out @sources.txt
if errorlevel 1 (
  echo.
  echo Compile failed - read the error above.
  pause
  exit /b 1
)

set "MODE=%~1"
if "%MODE%"=="" set "MODE=gui"
if /i "%MODE%"=="gui"     start "" javaw -cp out relief.ui.MainApp
if /i "%MODE%"=="tests"   java -cp out relief.HeapTests & pause
if /i "%MODE%"=="console" java -cp out relief.cli.ConsoleApp
if /i "%MODE%"=="bench"   java -Xmx2g -cp out relief.bench.Benchmark & pause
