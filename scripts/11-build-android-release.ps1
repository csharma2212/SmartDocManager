param([string]$Root)
. "$PSScriptRoot/common/Helpers.ps1"
foreach($envName in @('SMARTDOC_KEYSTORE','SMARTDOC_STORE_PASSWORD','SMARTDOC_KEY_ALIAS','SMARTDOC_KEY_PASSWORD')) {if(-not [Environment]::GetEnvironmentVariable($envName)){throw "Missing release signing setting: $envName"}}
Need-File $env:SMARTDOC_KEYSTORE
$gradle=Join-Path $Root 'android/gradlew.bat';Need-File $gradle
Push-Location (Join-Path $Root 'android')
try {Invoke-Checked $gradle @('--no-daemon','assembleRelease')} finally {Pop-Location}
$apk=Join-Path $Root 'android/app/build/outputs/apk/release/app-release.apk';Need-File $apk
$out=Join-Path $Root 'output/android';New-Item -ItemType Directory -Force -Path $out|Out-Null
Copy-Item -LiteralPath $apk -Destination (Join-Path $out 'SmartDoc-release.apk') -Force
