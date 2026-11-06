param(
    [string]$Root,
    [string]$OutputFile = ""
)
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
. "$PSScriptRoot/common/Helpers.ps1"
$Root = Assert-Root $Root

if (-not $OutputFile) {
    $timestamp = Get-Date -Format 'yyyyMMdd-HHmmss'
    $OutputFile = Join-Path $Root "SmartDocManager-source-$timestamp.zip"
}

Write-Status "Preparing clean distribution archive (excluding ephemeral caches, logs & binaries)..."

# Temporary staging directory in Temp
$tempStage = Join-Path ([IO.Path]::GetTempPath()) ("SmartDocManager-archive-" + [Guid]::NewGuid().ToString().Substring(0,8))
$stageRoot = Join-Path $tempStage "SmartDocManager"
New-Item -ItemType Directory -Path $stageRoot -Force | Out-Null

try {
    # 1. Root files
    foreach ($file in @('dev.ps1', 'README.md', 'README_Original.md', 'README_Revised_SmartDoc_POC.md', '.gitignore')) {
        $srcFile = Join-Path $Root $file
        if (Test-Path -LiteralPath $srcFile -PathType Leaf) {
            Copy-Item -LiteralPath $srcFile -Destination (Join-Path $stageRoot $file) -Force
        }
    }

    # 2. Scripts (all automation stages + common helpers)
    Copy-Item -Path (Join-Path $Root 'scripts') -Destination $stageRoot -Recurse -Force

    # 3. Shared Specification contract
    Copy-Item -Path (Join-Path $Root 'shared-spec') -Destination $stageRoot -Recurse -Force

    # 4. Windows Source (excluding .venv, __pycache__, .pytest_cache)
    $destWin = Join-Path $stageRoot 'windows'
    New-Item -ItemType Directory -Path $destWin -Force | Out-Null
    Copy-Item -LiteralPath (Join-Path $Root 'windows/app.py') -Destination (Join-Path $destWin 'app.py') -Force
    Copy-Item -LiteralPath (Join-Path $Root 'windows/requirements.txt') -Destination (Join-Path $destWin 'requirements.txt') -Force
    Copy-Item -Path (Join-Path $Root 'windows/assets') -Destination $destWin -Recurse -Force
    Copy-Item -Path (Join-Path $Root 'windows/smartdoc') -Destination $destWin -Recurse -Force
    Copy-Item -Path (Join-Path $Root 'windows/tests') -Destination $destWin -Recurse -Force
    
    # Clean any accidental pycache in windows
    Get-ChildItem -Path $destWin -Recurse -Directory -Filter "__pycache__" | Remove-Item -Recurse -Force -ErrorAction SilentlyContinue
    Get-ChildItem -Path $destWin -Recurse -Directory -Filter ".pytest_cache" | Remove-Item -Recurse -Force -ErrorAction SilentlyContinue

    # 5. Android Source (excluding .gradle, local.properties, build, *.jks)
    $destAndroid = Join-Path $stageRoot 'android'
    New-Item -ItemType Directory -Path $destAndroid -Force | Out-Null
    Copy-Item -LiteralPath (Join-Path $Root 'android/gradlew.bat') -Destination (Join-Path $destAndroid 'gradlew.bat') -Force
    if (Test-Path (Join-Path $Root 'android/gradlew')) {
        Copy-Item -LiteralPath (Join-Path $Root 'android/gradlew') -Destination (Join-Path $destAndroid 'gradlew') -Force
    }
    Copy-Item -Path (Join-Path $Root 'android/gradle') -Destination $destAndroid -Recurse -Force
    Copy-Item -LiteralPath (Join-Path $Root 'android/settings.gradle.kts') -Destination (Join-Path $destAndroid 'settings.gradle.kts') -Force
    Copy-Item -LiteralPath (Join-Path $Root 'android/build.gradle.kts') -Destination (Join-Path $destAndroid 'build.gradle.kts') -Force
    
    $destAndroidApp = Join-Path $destAndroid 'app'
    New-Item -ItemType Directory -Path $destAndroidApp -Force | Out-Null
    Copy-Item -LiteralPath (Join-Path $Root 'android/app/build.gradle.kts') -Destination (Join-Path $destAndroidApp 'build.gradle.kts') -Force
    Copy-Item -Path (Join-Path $Root 'android/app/src') -Destination $destAndroidApp -Recurse -Force

    # 6. Compress into destination zip
    if (Test-Path -LiteralPath $OutputFile) { Remove-Item -LiteralPath $OutputFile -Force }
    Compress-Archive -Path "$stageRoot/*" -DestinationPath $OutputFile -CompressionLevel Optimal

    $zipItem = Get-Item $OutputFile
    $sizeMB = [math]::Round($zipItem.Length / 1MB, 2)
    Write-Status "Clean distribution archive successfully generated!"
    Write-Host "  Archive: $OutputFile" -ForegroundColor Green
    Write-Host "  Size:    $sizeMB MB" -ForegroundColor Green
    Write-Host "  To use:  Extract on any machine and run: powershell -ExecutionPolicy Bypass -File .\dev.ps1 -Action Setup" -ForegroundColor Cyan
} finally {
    if (Test-Path -LiteralPath $tempStage) {
        Remove-Item -LiteralPath $tempStage -Recurse -Force -ErrorAction SilentlyContinue
    }
}
