param([string]$Root)
. "$PSScriptRoot/common/Helpers.ps1"
$Root = Assert-Root $Root
Resolve-AndroidSdk $Root | Out-Null
$gradle=Join-Path $Root 'android/gradlew.bat';Need-File $gradle
Need-File (Join-Path $Root 'android/gradle/wrapper/gradle-wrapper.jar')
$localBuild = if ($env:LOCALAPPDATA) { "$env:LOCALAPPDATA/AndroidDev/SmartDocManager-build/app" } else { $null }
$resultsDir = if ($localBuild -and (Test-Path $localBuild)) { Join-Path $localBuild 'test-results/testDebugUnitTest' } else { Join-Path $Root 'android/app/build/test-results/testDebugUnitTest' }
Remove-Item -Recurse -Force $resultsDir -ErrorAction SilentlyContinue
Push-Location (Join-Path $Root 'android')
try {Invoke-Checked $gradle @('--no-daemon','testDebugUnitTest')} finally {Pop-Location}
if (-not (Test-Path $resultsDir) -and $localBuild -and (Test-Path $localBuild)) {
    $resultsDir = Join-Path $localBuild 'test-results/testDebugUnitTest'
}
$xmlFiles = @(Get-ChildItem -Path $resultsDir -Filter "*.xml" -ErrorAction SilentlyContinue | Select-Object -ExpandProperty FullName)
Show-TestBlock "ANDROID TEST RESULTS" $xmlFiles
