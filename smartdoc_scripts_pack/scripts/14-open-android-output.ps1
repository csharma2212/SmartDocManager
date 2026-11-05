param([string]$Root)
. "$PSScriptRoot/common/Helpers.ps1"
$folder=Join-Path $Root 'output/android'
$debugApk = Join-Path $folder 'SmartDoc-debug.apk'
$releaseApk = Join-Path $folder 'SmartDoc-release.apk'
if (-not (Test-Path $debugApk) -and -not (Test-Path $releaseApk)) {
    throw "No APK found in $folder. Build Android first."
}
Start-Process explorer.exe -ArgumentList ('"'+$folder+'"')
