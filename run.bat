@echo off
cd /d "%~dp0"
title LibraryFlow - Java Library Management System
where java >nul 2>nul
if errorlevel 1 (
  echo Java was not found. Install JDK 17 or newer and try again.
  echo Download: https://www.oracle.com/java/technologies/downloads/
  pause
  exit /b 1
)
if not exist out mkdir out
echo Compiling LibraryFlow...
javac -encoding UTF-8 -d out src\LibraryFlowServer.java
if errorlevel 1 (
  echo.
  echo Build failed. Check the error above.
  pause
  exit /b 1
)
echo.
echo Starting LibraryFlow at http://127.0.0.1:8081
echo Keep this window open while using the app. Press Ctrl+C to stop.
java -cp out LibraryFlowServer
pause
