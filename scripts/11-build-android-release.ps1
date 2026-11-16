param([string]$Root)
. "$PSScriptRoot/common/Helpers.ps1"
$Root = Assert-Root $Root

# Ensure release keystore exists or generate it automatically
$releaseKeystore = Join-Path $Root 'keystores/smartdocmanager-release.jks'
if (-not (Test-Path -LiteralPath $releaseKeystore)) {
    Write-Status "Release keystore not found. Running scripts/20-setup-signing-and-sha1.ps1 to generate it..."
    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot '20-setup-signing-and-sha1.ps1') -ProjectRoot $Root
}

if (-not (Test-Path -LiteralPath $releaseKeystore)) {
    throw "Release keystore not found at: $releaseKeystore"
}

if (-not $env:SMARTDOC_KEYSTORE) {
    $env:SMARTDOC_KEYSTORE = $releaseKeystore
    $env:SMARTDOC_STORE_PASSWORD = 'SmartDoc@Release2026'
    $env:SMARTDOC_KEY_ALIAS = 'smartdocmanager'
    $env:SMARTDOC_KEY_PASSWORD = 'SmartDoc@Release2026'
}

$gradle = Join-Path $Root 'android/gradlew.bat'
Need-File $gradle
Push-Location (Join-Path $Root 'android')
try {
    Write-Status "Compiling Release APK with Gradle assembleRelease..."
    Invoke-Checked $gradle @('--no-daemon', 'assembleRelease')
} finally {
    Pop-Location
}

$localBuild = if ($env:LOCALAPPDATA) { "$env:LOCALAPPDATA/AndroidDev/SmartDocManager-build/app/outputs/apk/release/app-release.apk" } else { $null }
$apk = if ($localBuild -and (Test-Path $localBuild)) { $localBuild } else { Join-Path $Root 'android/app/build/outputs/apk/release/app-release.apk' }
Need-File $apk

$out = Join-Path $Root 'output/android'
New-Item -ItemType Directory -Force -Path $out | Out-Null
Copy-Item -LiteralPath $apk -Destination (Join-Path $out 'SmartDocManager-release.apk') -Force
Write-Status "Release APK successfully compiled and exported to: $(Join-Path $out 'SmartDocManager-release.apk')"
