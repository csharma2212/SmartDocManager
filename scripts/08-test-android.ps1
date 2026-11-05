param([string]$Root)
. "$PSScriptRoot/common/Helpers.ps1"
Resolve-AndroidSdk $Root | Out-Null
$gradle=Join-Path $Root 'android/gradlew.bat';Need-File $gradle
Need-File (Join-Path $Root 'android/gradle/wrapper/gradle-wrapper.jar')
$resultsDir = Join-Path $Root 'android/app/build/test-results/testDebugUnitTest'
Remove-Item -Recurse -Force $resultsDir -ErrorAction SilentlyContinue
Push-Location (Join-Path $Root 'android')
try {Invoke-Checked $gradle @('--no-daemon','testDebugUnitTest')} finally {Pop-Location}
$xmlFiles = @(Get-ChildItem -Path $resultsDir -Filter "*.xml" -ErrorAction SilentlyContinue | Select-Object -ExpandProperty FullName)
Show-TestBlock "ANDROID TEST RESULTS" $xmlFiles
