param([string]$Root)
$ErrorActionPreference = 'Stop'
. "$PSScriptRoot/common/Helpers.ps1"
$android = Join-Path $Root 'android'
if (-not (Test-Path -LiteralPath $android -PathType Container)) { throw "Android project directory missing: $android" }
$wrapperDir = Join-Path $android 'gradle/wrapper'
$required = @('gradlew.bat','gradlew','gradle/wrapper/gradle-wrapper.jar','gradle/wrapper/gradle-wrapper.properties')
$missing = @($required | Where-Object { -not (Test-Path -LiteralPath (Join-Path $android $_) -PathType Leaf) })
if ($missing.Count -eq 0) { Write-Status 'Gradle wrapper files already present.'; exit 0 }
Write-Status ('Missing Gradle wrapper files: ' + ($missing -join ', '))
if (-not (Get-Command gradle -ErrorAction SilentlyContinue)) {
 throw 'Gradle wrapper incomplete and bootstrap Gradle is not installed. Install a compatible Gradle distribution or configure an approved Gradle bootstrap method, then rerun Setup / Repair. Android Studio is not required.'
}
Push-Location $android
try {
 & gradle wrapper --gradle-version 8.9 --distribution-type bin
 if ($LASTEXITCODE -ne 0) { throw "Gradle wrapper generation failed (exit $LASTEXITCODE)." }
} finally { Pop-Location }
foreach ($rel in $required) { Need-File (Join-Path $android $rel) }
Write-Status 'Gradle wrapper files generated and verified.'