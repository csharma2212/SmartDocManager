param(
    [string]$Root,
    [string]$OutputFile = ""
)
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
. "$PSScriptRoot/common/Helpers.ps1"
$Root = Assert-Root $Root

$archiveDir = Join-Path $Root 'output/archive'
New-Item -ItemType Directory -Path $archiveDir -Force | Out-Null

if (-not $OutputFile) {
    $timestamp = Get-Date -Format 'yyyyMMdd-HHmmss'
    $OutputFile = Join-Path $archiveDir "SmartDocManager-source-$timestamp.zip"
}

Write-Status "Preparing clean distribution archive (excluding ephemeral caches, logs & binaries)..."

# Temporary staging directory in Temp
$tempStage = Join-Path ([IO.Path]::GetTempPath()) ("SmartDocManager-archive-" + [Guid]::NewGuid().ToString().Substring(0, 8))
$stageRoot = Join-Path $tempStage "SmartDocManager"
New-Item -ItemType Directory -Path $stageRoot -Force | Out-Null

try {
    # 1. Root files
    foreach ($file in @('dev.ps1', 'run-step.ps1', 'README.md', '.env.example', '.gitignore')) {
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
    Copy-Item -LiteralPath (Join-Path $Root 'windows/desktop_launcher.py') -Destination (Join-Path $destWin 'desktop_launcher.py') -Force
    Copy-Item -LiteralPath (Join-Path $Root 'windows/requirements.txt') -Destination (Join-Path $destWin 'requirements.txt') -Force
    Copy-Item -Path (Join-Path $Root 'windows/assets') -Destination $destWin -Recurse -Force
    Copy-Item -Path (Join-Path $Root 'windows/smartdoc') -Destination $destWin -Recurse -Force
    Copy-Item -Path (Join-Path $Root 'windows/tests') -Destination $destWin -Recurse -Force
    
    # Clean any accidental pycache in windows
    Get-ChildItem -Path $destWin -Recurse -Directory -Filter "__pycache__" | Remove-Item -Recurse -Force -ErrorAction SilentlyContinue
    Get-ChildItem -Path $destWin -Recurse -Directory -Filter ".pytest_cache" | Remove-Item -Recurse -Force -ErrorAction SilentlyContinue

    # 5. Hosted Backend (Cloudflare Worker source and tests, excluding node_modules, .wrangler)
    $destHosted = Join-Path $stageRoot 'hosted-backend/cloudflare'
    New-Item -ItemType Directory -Path $destHosted -Force | Out-Null
    Copy-Item -LiteralPath (Join-Path $Root 'hosted-backend/cloudflare/package.json') -Destination (Join-Path $destHosted 'package.json') -Force
    Copy-Item -LiteralPath (Join-Path $Root 'hosted-backend/cloudflare/wrangler.toml') -Destination (Join-Path $destHosted 'wrangler.toml') -Force
    Copy-Item -Path (Join-Path $Root 'hosted-backend/cloudflare/src') -Destination $destHosted -Recurse -Force
    Copy-Item -Path (Join-Path $Root 'hosted-backend/cloudflare/test') -Destination $destHosted -Recurse -Force

    # 6. Android Source (excluding .gradle, local.properties, build, *.jks)
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

    # 7. Compress into destination zip
    if (Test-Path -LiteralPath $OutputFile) { Remove-Item -LiteralPath $OutputFile -Force }
    Compress-Archive -Path "$stageRoot/*" -DestinationPath $OutputFile -CompressionLevel Optimal

    $zipItem = Get-Item $OutputFile
    $sizeMB = [math]::Round($zipItem.Length / 1MB, 2)

    # Retain only the latest 2 archive snapshots to prevent disk clutter
    Get-ChildItem -Path $archiveDir -Filter "SmartDocManager-source-*.zip" -File | 
    Sort-Object LastWriteTime -Descending | 
    Select-Object -Skip 2 | 
    Remove-Item -Force -ErrorAction SilentlyContinue

    Write-Status "Clean distribution archive successfully generated!"
    Write-Host "  Archive: $OutputFile" -ForegroundColor Green
    Write-Host "  Size:    $sizeMB MB" -ForegroundColor Green
    Write-Host "  To use:  Extract on any machine and run: powershell -ExecutionPolicy Bypass -File .\dev.ps1 -Action Setup" -ForegroundColor Cyan
}
finally {
    if (Test-Path -LiteralPath $tempStage) {
        Remove-Item -LiteralPath $tempStage -Recurse -Force -ErrorAction SilentlyContinue
    }
}
