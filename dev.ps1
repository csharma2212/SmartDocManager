param(
    [string]$ProjectRoot = $PSScriptRoot,
    [ValidateSet('Menu','Setup','Verify','Test','BuildWindows','BuildAndroid','BuildAndroidDebug','BuildAndroidRelease','InitKeystore','BuildAll','LaunchWindows','OpenApks','DeployDebug','Archive')][string]$Action='Menu',
    [int]$Port = 8501,
    [string]$Address = 'localhost'
)
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'scripts/common/Helpers.ps1')
$Root=Assert-Root $ProjectRoot
$logDir=Join-Path $Root 'logs'; New-Item -ItemType Directory -Force -Path $logDir | Out-Null
$runId=Get-Date -Format 'yyyyMMdd-HHmmss';$report=Join-Path $logDir "run-$runId.txt"
function Stage([string]$File) {
 $path=Join-Path $Root "scripts/$File"; Need-File $path
 $start=Get-Date; $title=$File; Write-Host "`n=== START $title ===" -ForegroundColor Cyan
 Add-Content -LiteralPath $report -Value "START $title $($start.ToString('o'))"
 try {
  & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $path -Root $Root
  if ($LASTEXITCODE -ne 0) { throw "Exit code $LASTEXITCODE" }
  $elapsed=((Get-Date)-$start).TotalSeconds
  Add-Content -LiteralPath $report -Value "PASS $title ${elapsed}s"
  Write-Host "=== PASS $title ($([math]::Round($elapsed,1))s) ===" -ForegroundColor Green
 } catch {
  Add-Content -LiteralPath $report -Value "FAIL $title : $_"
  throw "Stage $title failed: $_"
 }
}
function Choose-Action {
 Write-Host "`n======================================================================"
 Write-Host "           SmartDocManager Master Controller ($Root)"
 Write-Host "======================================================================"
 Write-Host "  1  Setup / Repair Environment"
 Write-Host "  2  Verify System Readiness & Audit"
 Write-Host "  3  Run Full Pre-Build Test Suite (Windows + Android)"
 Write-Host "  4  Build & Launch Windows Desktop Application"
 Write-Host "  5  Build Android (Debug + Release)"
 Write-Host "  6  Build Android Debug APK Only"
 Write-Host "  7  Build Android Release APK Only"
 Write-Host "  8  Initialize Keystore (Android Release Signing Setup)"
 Write-Host "  9  Build All (Windows Desktop + Android APKs)"
 Write-Host " 10  Launch Windows Desktop Application Directly"
 Write-Host " 11  Open APK Output Folder"
 Write-Host " 12  Deploy Debug APK to Phone & Launch (ADB)"
 Write-Host " 13  Create Clean Source Archive (.zip)"
 Write-Host "  0  Exit"
 Write-Host "======================================================================"
 switch (Read-Host 'Choose an action [0-13]') {
 '1' {'Setup'}
 '2' {'Verify'}
 '3' {'Test'}
 '4' {'BuildWindows'}
 '5' {'BuildAndroid'}
 '6' {'BuildAndroidDebug'}
 '7' {'BuildAndroidRelease'}
 '8' {'InitKeystore'}
 '9' {'BuildAll'}
 '10' {
     $customPort = Read-Host 'Enter port (press Enter for default 8501)'
     if ($customPort -match '^\d+$') { $script:Port = [int]$customPort }
     'LaunchWindows'
 }
 '11' {'OpenApks'}
 '12' {'DeployDebug'}
 '13' {'Archive'}
 '0' {'Exit'}
 default {throw 'Invalid menu selection'}
 }
}
if ($Action -eq 'Menu') { $Action=Choose-Action }
if ($Action -eq 'Exit') {exit 0}
$pre=@('01-check-resources.ps1','02-check-dependencies.ps1')
$tests=@('06-test-shared.ps1','07-test-windows.ps1','08-test-android.ps1')
try {
 switch ($Action) {
 'Setup' { Stage '01-check-resources.ps1'; Stage '02-check-dependencies.ps1'; Stage '04-create-scaffold.ps1'; Stage '03-install-dependencies.ps1'; Stage '16-init-gradle-wrapper.ps1'; Stage '05-verify-readiness.ps1' }
 'Verify' { Stage '01-check-resources.ps1'; Stage '02-check-dependencies.ps1'; Stage '05-verify-readiness.ps1' }
 'Test' { foreach($x in $pre){Stage $x};Stage '05-verify-readiness.ps1';foreach($x in $tests){Stage $x} }
 'BuildWindows' {foreach($x in $pre){Stage $x};Stage '05-verify-readiness.ps1';foreach($x in $tests){Stage $x};Stage '09-build-windows.ps1';Stage '17-create-windows-shortcut.ps1';Stage '13-launch-windows.ps1'}
 'BuildAndroid' {foreach($x in $pre){Stage $x};Stage '05-verify-readiness.ps1';foreach($x in $tests){Stage $x};Stage '10-build-android-debug.ps1';Stage '11-build-android-release.ps1';Stage '12-verify-artifacts.ps1';Stage '14-open-android-output.ps1'}
 'BuildAndroidDebug' {foreach($x in $pre){Stage $x};Stage '05-verify-readiness.ps1';foreach($x in $tests){Stage $x};Stage '10-build-android-debug.ps1';Stage '12-verify-artifacts.ps1';Stage '14-open-android-output.ps1'}
 'BuildAndroidRelease' {foreach($x in $pre){Stage $x};Stage '05-verify-readiness.ps1';foreach($x in $tests){Stage $x};Stage '11-build-android-release.ps1';Stage '12-verify-artifacts.ps1';Stage '14-open-android-output.ps1'}
 'InitKeystore' {Stage '18-init-keystore.ps1'}
 'BuildAll' {foreach($x in $pre){Stage $x};Stage '05-verify-readiness.ps1';foreach($x in $tests){Stage $x};Stage '09-build-windows.ps1';Stage '10-build-android-debug.ps1';Stage '11-build-android-release.ps1';Stage '12-verify-artifacts.ps1';Stage '17-create-windows-shortcut.ps1';Stage '13-launch-windows.ps1';Stage '14-open-android-output.ps1'}
 'LaunchWindows' {
     $launchScript = Join-Path $Root 'scripts/13-launch-windows.ps1'
     & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $launchScript -Root $Root -Port $Port -Address $Address
     if ($LASTEXITCODE -ne 0) { throw "Exit code $LASTEXITCODE" }
 }
 'OpenApks' {Stage '14-open-android-output.ps1'}
 'DeployDebug' {Stage '15-deploy-android-debug.ps1'}
 'Archive' {Stage '19-create-clean-archive.ps1'}
 }
 Add-Content -LiteralPath $report -Value 'WORKFLOW SUCCEEDED (selected action)' 
 Write-Host "`nWORKFLOW SUCCEEDED ($Action)" -ForegroundColor Green
} catch {
 Add-Content -LiteralPath $report -Value "BUILD FAILED: $_"
 Write-Host "`nBUILD FAILED: $_`nReport: $report" -ForegroundColor Red
 exit 1
}
Write-Host "Report: $report"
