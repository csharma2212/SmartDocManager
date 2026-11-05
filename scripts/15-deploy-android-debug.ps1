param([string]$Root)
. "$PSScriptRoot/common/Helpers.ps1"
Need-Command 'adb'
$apk=Join-Path $Root 'output/android/SmartDoc-debug.apk';Need-File $apk
$ok=Read-Host 'Install debug APK on connected, authorized phone? (Y/N)'
if($ok -notmatch '^[Yy]$'){throw 'Deployment not approved'}
Invoke-Checked 'adb' @('install','-r',$apk)
