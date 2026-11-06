param([string]$Root)
. "$PSScriptRoot/common/Helpers.ps1"
$Root = Assert-Root $Root
$folder=Join-Path $Root 'output/android'
$debugApk = Join-Path $folder 'SmartDocManager-debug.apk'
$releaseApk = Join-Path $folder 'SmartDocManager-release.apk'
if (-not (Test-Path $debugApk) -and -not (Test-Path $releaseApk) -and -not (Test-Path (Join-Path $folder 'SmartDoc-debug.apk'))) {
    throw "No APK found in $folder. Build Android first."
}
Start-Process explorer.exe -ArgumentList ('"'+$folder+'"')
