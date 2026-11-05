param([string]$Root)
. "$PSScriptRoot/common/Helpers.ps1"
$sdk = Resolve-AndroidSdk $Root
if(-not $sdk){throw 'Android SDK required for apksigner (set ANDROID_HOME or configure android/local.properties)'}
$tools=Get-ChildItem -Path (Join-Path $sdk 'build-tools') -Directory -ErrorAction SilentlyContinue | Sort-Object Name -Descending
if(-not $tools){throw 'Android SDK build-tools missing'}
$signer=Join-Path $tools[0].FullName 'apksigner.bat';Need-File $signer
$kinds = @('debug')
if (Test-Path (Join-Path $Root 'output/android/SmartDoc-release.apk')) { $kinds += 'release' }
foreach($kind in $kinds){
 $apk=Join-Path $Root "output/android/SmartDoc-$kind.apk";Need-File $apk
 if((Get-Item $apk).Length -lt 1024){throw "APK unexpectedly small: $apk"}
 Invoke-Checked $signer @('verify','--verbose',$apk)
 $hash=(Get-FileHash -LiteralPath $apk -Algorithm SHA256).Hash
 Write-Status "$kind APK SHA256: $hash"
}
Write-Warning 'Application ID/version and certificate identity still require additional project-specific validation.'
