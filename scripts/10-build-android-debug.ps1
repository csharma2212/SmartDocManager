param([string]$Root)
. "$PSScriptRoot/common/Helpers.ps1"
Resolve-AndroidSdk $Root | Out-Null
$gradle=Join-Path $Root 'android/gradlew.bat';Need-File $gradle
Push-Location (Join-Path $Root 'android')
try {Invoke-Checked $gradle @('--no-daemon','assembleDebug')} finally {Pop-Location}
$apk=Join-Path $Root 'android/app/build/outputs/apk/debug/app-debug.apk'
Need-File $apk
$out=Join-Path $Root 'output/android';New-Item -ItemType Directory -Force -Path $out|Out-Null
Copy-Item -LiteralPath $apk -Destination (Join-Path $out 'SmartDoc-debug.apk') -Force
