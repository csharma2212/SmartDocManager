param([string]$Root)
. "$PSScriptRoot/common/Helpers.ps1"
$Root = Assert-Root $Root
Resolve-AndroidSdk $Root | Out-Null
$gradle=Join-Path $Root 'android/gradlew.bat';Need-File $gradle
Push-Location (Join-Path $Root 'android')
try {Invoke-Checked $gradle @('--no-daemon','assembleDebug')} finally {Pop-Location}
$localBuild = if ($env:LOCALAPPDATA) { "$env:LOCALAPPDATA/AndroidDev/SmartDocManager-build/app/outputs/apk/debug/app-debug.apk" } else { $null }
$apk = if ($localBuild -and (Test-Path $localBuild)) { $localBuild } else { Join-Path $Root 'android/app/build/outputs/apk/debug/app-debug.apk' }
Need-File $apk
$out=Join-Path $Root 'output/android';New-Item -ItemType Directory -Force -Path $out|Out-Null
Copy-Item -LiteralPath $apk -Destination (Join-Path $out 'SmartDocManager-debug.apk') -Force
