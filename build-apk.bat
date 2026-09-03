@echo off
setlocal
title IB-TECH ePINs APK Builder
echo ==========================================
echo       IB-TECH ePINs Android APK Builder
echo ==========================================
echo.

where adb >nul 2>&1
if %errorlevel%==0 (
  echo Android SDK tools detected.
) else (
  echo WARNING: Android SDK tools are not on PATH.
  echo Open this project in Android Studio and install the SDK/Build Tools first.
  echo.
)

if exist "%~dp0gradlew.bat" (
  echo Building debug APK...
  call "%~dp0gradlew.bat" assembleDebug
  if %errorlevel% neq 0 goto :fail
) else (
  echo ERROR: gradlew.bat was not found.
  echo Open the project folder in Android Studio and let Gradle sync first.
  goto :fail
)

echo.
echo BUILD SUCCESSFUL
echo APK location:
echo %~dp0app\build\outputs\apk\debug\app-debug.apk
echo.
pause
exit /b 0

:fail
echo.
echo BUILD FAILED - see the messages above.
pause
exit /b 1
