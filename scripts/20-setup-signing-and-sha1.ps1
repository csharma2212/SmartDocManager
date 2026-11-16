param(
    [string]$ProjectRoot = (Resolve-Path "$PSScriptRoot\..").Path,
    [string]$KeystoreDir = "$PSScriptRoot\..\keystores",
    [string]$Alias = "smartdocmanager",
    [string]$Password = "SmartDoc@Release2026",
    [string]$OutputFile = "signing-fingerprints.txt"
)

$ErrorActionPreference = 'Stop'
Write-Host "======================================================================" -ForegroundColor Cyan
Write-Host "    SmartDocManager Android Keystore & SHA-1 Fingerprint Generator    " -ForegroundColor Cyan
Write-Host "======================================================================" -ForegroundColor Cyan

# Ensure keytool is available
if (-not (Get-Command 'keytool' -ErrorAction SilentlyContinue)) {
    throw "Missing tool: keytool (Java JDK). Please ensure JDK 17+ is installed and on PATH."
}

# Ensure keystore directory exists (git-ignored)
$resolvedKeystoreDir = [System.IO.Path]::GetFullPath($KeystoreDir)
if (-not (Test-Path $resolvedKeystoreDir)) {
    New-Item -ItemType Directory -Force -Path $resolvedKeystoreDir | Out-Null
}

# 1. Setup Debug Keystore
$debugKeystore = Join-Path $resolvedKeystoreDir "debug.keystore"
if (-not (Test-Path -LiteralPath $debugKeystore)) {
    Write-Host "[1/3] Generating standard Android debug keystore..." -ForegroundColor Yellow
    & keytool -genkeypair -v `
        -keystore $debugKeystore `
        -alias androiddebugkey `
        -storepass android `
        -keypass android `
        -keyalg RSA `
        -keysize 2048 `
        -validity 10000 `
        -dname "CN=Android Debug,O=Android,C=US"
    if ($LASTEXITCODE -ne 0) { throw "Failed to generate debug keystore." }
    Write-Host "  -> Created debug keystore at: $debugKeystore" -ForegroundColor Green
} else {
    Write-Host "[1/3] Found existing debug keystore at: $debugKeystore" -ForegroundColor Green
}

# 2. Setup Self-Signed Release Keystore
$releaseKeystore = Join-Path $resolvedKeystoreDir "smartdocmanager-release.jks"
if (-not (Test-Path -LiteralPath $releaseKeystore)) {
    Write-Host "[2/3] Generating self-signed release keystore..." -ForegroundColor Yellow
    & keytool -genkeypair -v `
        -keystore $releaseKeystore `
        -alias $Alias `
        -keyalg RSA `
        -keysize 2048 `
        -validity 10000 `
        -storepass $Password `
        -keypass $Password `
        -dname "CN=SmartDocManager,OU=Release,O=Personal,C=IN"
    if ($LASTEXITCODE -ne 0) { throw "Failed to generate release keystore." }
    Write-Host "  -> Created release keystore at: $releaseKeystore" -ForegroundColor Green
} else {
    Write-Host "[2/3] Found existing release keystore at: $releaseKeystore" -ForegroundColor Green
}

# Configure environment variables for signing
$env:SMARTDOC_KEYSTORE = $releaseKeystore
$env:SMARTDOC_STORE_PASSWORD = $Password
$env:SMARTDOC_KEY_ALIAS = $Alias
$env:SMARTDOC_KEY_PASSWORD = $Password

try {
    [System.Environment]::SetEnvironmentVariable('SMARTDOC_KEYSTORE', $releaseKeystore, 'User')
    [System.Environment]::SetEnvironmentVariable('SMARTDOC_STORE_PASSWORD', $Password, 'User')
    [System.Environment]::SetEnvironmentVariable('SMARTDOC_KEY_ALIAS', $Alias, 'User')
    [System.Environment]::SetEnvironmentVariable('SMARTDOC_KEY_PASSWORD', $Password, 'User')
} catch {}

# 3. Extract SHA-1 fingerprints
Write-Host "[3/3] Extracting SHA-1 fingerprints..." -ForegroundColor Yellow

$debugOutput = & keytool -list -v -keystore $debugKeystore -alias androiddebugkey -storepass android
$debugText = ($debugOutput -join "`n")
$debugSha1 = ""
if ($debugText -match 'SHA1:\s+([A-Fa-f0-9:]{59})') {
    $debugSha1 = $Matches[1].ToUpper()
}

$releaseOutput = & keytool -list -v -keystore $releaseKeystore -alias $Alias -storepass $Password
$releaseText = ($releaseOutput -join "`n")
$releaseSha1 = ""
if ($releaseText -match 'SHA1:\s+([A-Fa-f0-9:]{59})') {
    $releaseSha1 = $Matches[1].ToUpper()
}

# 4. Format and save to file
$outPath = Join-Path $ProjectRoot $OutputFile
$report = @"
================================================================================
           SmartDocManager Android OAuth SHA-1 Fingerprints
================================================================================
Generated on: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')
Application ID: com.smartdoc.poc

1. DEBUG CERTIFICATE (Used when running in Android Studio or debug builds):
   Keystore: $debugKeystore
   Alias:    androiddebugkey
   SHA-1:    $debugSha1

2. RELEASE CERTIFICATE (Used when building release APK):
   Keystore: $releaseKeystore
   Alias:    $Alias
   SHA-1:    $releaseSha1

Google Cloud Console Registration Instructions:
--------------------------------------------------------------------------------
1. Open Google Cloud Console -> APIs & Services -> Credentials.
2. Select or create your 'Android' OAuth 2.0 Client ID.
3. Package Name: com.smartdoc.poc
4. Add the DEBUG SHA-1 fingerprint above for testing on emulator/device.
5. Add the RELEASE SHA-1 fingerprint above for installed release builds.
================================================================================
"@

[IO.File]::WriteAllText($outPath, $report, [Text.UTF8Encoding]::new($false))

Write-Host "`nSHA-1 FINGERPRINTS READY:" -ForegroundColor Cyan
Write-Host "----------------------------------------------------------------------"
Write-Host "DEBUG SHA-1:   $debugSha1" -ForegroundColor Yellow
Write-Host "RELEASE SHA-1: $releaseSha1" -ForegroundColor Green
Write-Host "----------------------------------------------------------------------"
Write-Host "Saved fingerprint reference to: $OutputFile (Git-ignored)" -ForegroundColor Cyan
Write-Host "Release signing environment variables configured." -ForegroundColor Green
