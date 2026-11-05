param([string]$ProjectRoot = $PSScriptRoot,[ValidateSet('Menu','Setup','Verify','Test','BuildWindows','BuildAndroid','BuildAll','LaunchWindows','OpenApks','DeployDebug')][string]$Action='Menu')
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
 Write-Host "`nSmartDoc Development Controller ($Root)"
 Write-Host '1 Setup / Repair  2 Verify  3 Test  4 Build Windows  5 Build Android'
 Write-Host '6 Build All  7 Launch Windows  8 Open APK Folder  9 Deploy Debug APK  0 Exit'
 switch (Read-Host 'Choose') {
 '1' {'Setup'} '2' {'Verify'} '3' {'Test'} '4' {'BuildWindows'} '5' {'BuildAndroid'}
 '6' {'BuildAll'} '7' {'LaunchWindows'} '8' {'OpenApks'} '9' {'DeployDebug'} '0' {'Exit'}
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
 'BuildAll' {foreach($x in $pre){Stage $x};Stage '05-verify-readiness.ps1';foreach($x in $tests){Stage $x};Stage '09-build-windows.ps1';Stage '10-build-android-debug.ps1';Stage '11-build-android-release.ps1';Stage '12-verify-artifacts.ps1';Stage '17-create-windows-shortcut.ps1';Stage '13-launch-windows.ps1';Stage '14-open-android-output.ps1'}
 'LaunchWindows' {Stage '13-launch-windows.ps1'}
 'OpenApks' {Stage '14-open-android-output.ps1'}
 'DeployDebug' {Stage '15-deploy-android-debug.ps1'}
 }
 Add-Content -LiteralPath $report -Value 'WORKFLOW SUCCEEDED (selected action)' 
 Write-Host "`nWORKFLOW SUCCEEDED ($Action)" -ForegroundColor Green
} catch {
 Add-Content -LiteralPath $report -Value "BUILD FAILED: $_"
 Write-Host "`nBUILD FAILED: $_`nReport: $report" -ForegroundColor Red
 exit 1
}
Write-Host "Report: $report"
