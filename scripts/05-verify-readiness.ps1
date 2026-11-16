param([string]$Root)
. "$PSScriptRoot/common/Helpers.ps1"
$Root = Assert-Root $Root
foreach($f in @('shared-spec/taxonomy.json','shared-spec/result-schema.json','shared-spec/app-config.json','windows/app.py','windows/desktop_launcher.py','windows/smartdoc/core.py','windows/smartdoc/indian_taxonomy.py','windows/requirements.txt','android/app/build.gradle.kts','android/settings.gradle.kts','android/app/src/main/AndroidManifest.xml')){Need-File (Join-Path $Root $f)}
Need-Command 'python'
Write-Status 'Source structure verified. Build-specific tools checked in their respective test/build stages.'

# Android Gradle Wrapper must be complete before Android tests begin.
$androidDir = Join-Path $Root 'android'
foreach ($rel in @('gradlew.bat','gradlew','gradle/wrapper/gradle-wrapper.jar','gradle/wrapper/gradle-wrapper.properties')) {
 $candidate = Join-Path $androidDir $rel
 if (-not (Test-Path -LiteralPath $candidate -PathType Leaf)) {
  throw "Missing Android Gradle wrapper component: $candidate. Run Setup / Repair first."
 }
}
Write-Status 'Android Gradle wrapper components verified.'