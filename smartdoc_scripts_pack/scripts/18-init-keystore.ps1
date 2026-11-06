param(
    [string]$Root,
    [string]$KeystorePath = "$env:USERPROFILE\.android\smartdocmanager-release.jks",
    [string]$Alias = "smartdocmanager",
    [string]$Password = ""
)
. "$PSScriptRoot/common/Helpers.ps1"
$Root = Assert-Root $Root
Need-Command 'keytool'

if (-not $Password) {
    $Password = Read-Host 'Enter a password for the release keystore (min 6 characters)'
    if ($Password.Length -lt 6) { throw 'Password must be at least 6 characters long.' }
}

$parent = Split-Path $KeystorePath
if (-not (Test-Path $parent)) { New-Item -ItemType Directory -Path $parent -Force | Out-Null }

if (Test-Path -LiteralPath $KeystorePath) {
    Write-Warning "Keystore already exists at: $KeystorePath"
    $overwrite = Read-Host "Do you want to reuse existing keystore? (Y to keep, N to overwrite)"
    if ($overwrite -match '^[Nn]$') {
        Remove-Item -LiteralPath $KeystorePath -Force
    }
}

if (-not (Test-Path -LiteralPath $KeystorePath)) {
    Write-Status "Generating release keystore at: $KeystorePath"
    & keytool -genkeypair -v `
        -keystore $KeystorePath `
        -alias $Alias `
        -keyalg RSA `
        -keysize 2048 `
        -validity 10000 `
        -storepass $Password `
        -keypass $Password `
        -dname "CN=SmartDocManager User, OU=SmartDocManager, O=Personal, C=IN"
    if ($LASTEXITCODE -ne 0) { throw "Keytool failed to generate keystore." }
    Write-Status "Keystore generated successfully!"
}

# Set environment variables for current process session
$env:SMARTDOC_KEYSTORE = $KeystorePath
$env:SMARTDOC_STORE_PASSWORD = $Password
$env:SMARTDOC_KEY_ALIAS = $Alias
$env:SMARTDOC_KEY_PASSWORD = $Password

# Set permanently for Windows user account
[System.Environment]::SetEnvironmentVariable('SMARTDOC_KEYSTORE', $KeystorePath, 'User')
[System.Environment]::SetEnvironmentVariable('SMARTDOC_STORE_PASSWORD', $Password, 'User')
[System.Environment]::SetEnvironmentVariable('SMARTDOC_KEY_ALIAS', $Alias, 'User')
[System.Environment]::SetEnvironmentVariable('SMARTDOC_KEY_PASSWORD', $Password, 'User')

Write-Status "Release signing environment variables configured permanently (User level):"
Write-Host "  SMARTDOC_KEYSTORE       = $KeystorePath"
Write-Host "  SMARTDOC_KEY_ALIAS      = $Alias"
Write-Host "  SMARTDOC_STORE_PASSWORD = (configured)"
Write-Host "  SMARTDOC_KEY_PASSWORD   = (configured)"
Write-Status "You can now run: powershell -ExecutionPolicy Bypass -File .\scripts\11-build-android-release.ps1"
