param([string]$Root)
. "$PSScriptRoot/common/Helpers.ps1"
$Root = Assert-Root $Root
$missing = @()
foreach($envName in @('SMARTDOC_KEYSTORE','SMARTDOC_STORE_PASSWORD','SMARTDOC_KEY_ALIAS','SMARTDOC_KEY_PASSWORD')) {
    if(-not [Environment]::GetEnvironmentVariable($envName)){ $missing += $envName }
}
if ($missing.Count -gt 0) {
    Write-Warning "Skipping Release APK build (Missing release signing settings: $($missing -join ', '))."
    Write-Status "Debug APK is already built and ready at output/android/SmartDocManager-debug.apk. To enable Release APK builds, configure SMARTDOC_KEYSTORE."
    return
}
Need-File $env:SMARTDOC_KEYSTORE
$gradle=Join-Path $Root 'android/gradlew.bat';Need-File $gradle
Push-Location (Join-Path $Root 'android')
try {Invoke-Checked $gradle @('--no-daemon','assembleRelease')} finally {Pop-Location}
$localBuild = if ($env:LOCALAPPDATA) { "$env:LOCALAPPDATA/AndroidDev/SmartDocManager-build/app/outputs/apk/release/app-release.apk" } else { $null }
$apk = if ($localBuild -and (Test-Path $localBuild)) { $localBuild } else { Join-Path $Root 'android/app/build/outputs/apk/release/app-release.apk' }
Need-File $apk
$out=Join-Path $Root 'output/android';New-Item -ItemType Directory -Force -Path $out|Out-Null
Copy-Item -LiteralPath $apk -Destination (Join-Path $out 'SmartDocManager-release.apk') -Force
