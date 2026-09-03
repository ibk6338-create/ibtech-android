$ErrorActionPreference = "Stop"
Write-Host "IB-TECH ePINs Android APK Builder" -ForegroundColor Green
if (!(Test-Path "$PSScriptRoot\gradlew.bat")) {
  throw "gradlew.bat was not found. Open the project in Android Studio and let Gradle sync first."
}
& "$PSScriptRoot\gradlew.bat" assembleDebug
Write-Host ""
Write-Host "APK: $PSScriptRoot\app\build\outputs\apk\debug\app-debug.apk" -ForegroundColor Green
