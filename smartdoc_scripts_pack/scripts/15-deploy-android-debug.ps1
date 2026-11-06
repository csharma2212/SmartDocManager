param(
    [string]$Root,
    [switch]$NoPrompt,
    [switch]$StreamLogs
)
$ErrorActionPreference = 'Stop'
. "$PSScriptRoot/common/Helpers.ps1"
$Root = Assert-Root $Root

# Locate ADB
try {
    Need-Command 'adb'
} catch {
    $sdk = Resolve-AndroidSdk $Root
    if ($sdk) {
        $adbCandidate = Join-Path $sdk 'platform-tools/adb.exe'
        if (Test-Path $adbCandidate) {
            $env:PATH = "$(Split-Path $adbCandidate);$env:PATH"
            Write-Status "Discovered adb in Android SDK platform-tools."
        }
    }
}
Need-Command 'adb'

# Verify APK exists
$apk = Join-Path $Root 'output/android/SmartDocManager-debug.apk'
if (-not (Test-Path $apk)) {
    $apk = Join-Path $Root 'output/android/SmartDoc-debug.apk'
}
if (-not (Test-Path $apk)) {
    Write-Warning "Debug APK not found at: $apk"
    Write-Status "Building Debug APK now via scripts/10-build-android-debug.ps1..."
    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot '10-build-android-debug.ps1') -Root $Root
    if ($LASTEXITCODE -ne 0) { throw "Debug APK build failed." }
    $apk = Join-Path $Root 'output/android/SmartDocManager-debug.apk'
}
Need-File $apk

Write-Status "Scanning for connected Android devices via ADB..."
$rawDevices = & adb devices -l
$deviceLines = $rawDevices | Where-Object { $_ -match '\b(device|unauthorized|offline)\b' -and $_ -notmatch 'List of devices attached' }

if (-not $deviceLines) {
    Write-Host "`n" -NoNewline
    Write-Warning "No connected Android devices detected!"
    Write-Host @"
======================================================================
  HOW TO CONNECT YOUR REAL ANDROID PHONE (e.g. Realme C63 5G)
======================================================================
1. Connect your phone to this PC using a quality USB data cable.
2. On your phone, enable Developer Mode:
   - Open Settings -> About Device -> Version.
   - Tap 'Version Number' (or 'Build Number') 7 times rapidly.
   - Enter your phone lock PIN if asked -> 'You are now in Developer Mode!'
3. Enable USB Debugging:
   - Open Settings -> Additional Settings (or System) -> Developer Options.
   - Turn ON 'USB Debugging'.
   - If available (Realme/Oppo/Xiaomi), also turn ON 'Install via USB'.
4. Set USB Connection Mode:
   - When plugged in, swipe down notification shade and select 'File Transfer / Android Auto'.
5. Authorize USB Debugging:
   - Look at your phone screen for 'Allow USB debugging?' popup.
   - Check 'Always allow from this computer' and tap 'Allow' or 'OK'.
======================================================================
"@ -ForegroundColor Yellow

    if (-not $NoPrompt) {
        $retry = Read-Host "After completing the steps above, press Enter to re-scan (or 'Q' to quit)"
        if ($retry -match '^[Qq]$') { throw "Deployment cancelled: No device connected." }
        $rawDevices = & adb devices -l
        $deviceLines = $rawDevices | Where-Object { $_ -match '\b(device|unauthorized|offline)\b' -and $_ -notmatch 'List of devices attached' }
        if (-not $deviceLines) { throw "No Android device detected. Please verify USB cable and developer mode." }
    } else {
        throw "No Android device detected."
    }
}

# Check for unauthorized or offline devices
$unauth = $deviceLines | Where-Object { $_ -match '\bunauthorized\b' }
if ($unauth) {
    Write-Warning "Device is connected but NOT AUTHORIZED!"
    Write-Host "Please unlock your phone screen, check 'Always allow from this computer', and tap 'Allow'." -ForegroundColor Yellow
    Start-Sleep -Seconds 3
}

$authorizedDevices = $deviceLines | Where-Object { $_ -match '\bdevice\b' }
if (-not $authorizedDevices) {
    throw "No authorized device ready for installation. Please approve the USB debugging prompt on your phone."
}

Write-Status "Connected Authorized Device(s):"
$authorizedDevices | ForEach-Object { Write-Host "  $_" -ForegroundColor Green }

if (-not $NoPrompt) {
    $confirm = Read-Host "Install SmartDocManager-debug.apk on connected device? (Y/N)"
    if ($confirm -notmatch '^[Yy]$') {
        Write-Warning "Deployment cancelled by user."
        return
    }
}

Write-Status "Installing APK to device: $apk ..."
& adb install -r -d $apk
if ($LASTEXITCODE -ne 0) {
    throw "ADB install failed with exit code $LASTEXITCODE. If installation was blocked, make sure 'Install via USB' is enabled in Developer Options."
}
Write-Status "SmartDocManager installed successfully on device!"

# Offer to launch MainActivity
$launchApp = $true
if (-not $NoPrompt) {
    $launchChoice = Read-Host "Launch SmartDocManager on device now? (Y/N)"
    if ($launchChoice -notmatch '^[Yy]$') { $launchApp = $false }
}

if ($launchApp) {
    Write-Status "Launching SmartDocManager (com.smartdoc.poc/.MainActivity)..."
    & adb shell am start -n com.smartdoc.poc/.MainActivity
    if ($LASTEXITCODE -eq 0) {
        Write-Status "App launched on phone screen!"
    }
}

# Real-time error catching guide
Write-Host @"

======================================================================
  REAL-TIME RUNTIME ERROR & CRASH DIAGNOSTICS (LOGCAT)
======================================================================
To monitor live logs, catch uncaught exceptions, and diagnose errors:

Command:
  adb logcat -v time | Select-String "SmartDoc|com.smartdoc.poc|AndroidRuntime|FATAL|Exception"

Tip: Keep this command running in a separate PowerShell window while
testing features, selecting PDFs, or executing Gemini classification.
======================================================================
"@ -ForegroundColor Cyan

if ($StreamLogs) {
    Write-Status "Starting live logcat stream (Press Ctrl+C to stop)..."
    & adb logcat -v time | Select-String "SmartDoc|com.smartdoc.poc|AndroidRuntime|FATAL|Exception"
}
