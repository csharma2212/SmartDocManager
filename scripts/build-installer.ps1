param([string]$Root)
. "$PSScriptRoot/common/Helpers.ps1"
$Root = Assert-Root $Root
$py = Join-Path $Root 'windows/.venv/Scripts/python.exe'; Need-File $py
$pyinstaller = Join-Path $Root 'windows/.venv/Scripts/pyinstaller.exe'; Need-File $pyinstaller

$spec = Join-Path $Root 'windows/SmartDocManager_Onedir.spec'; Need-File $spec
$iss = Join-Path $Root 'windows/installer.iss'; Need-File $iss

# 1. Find Inno Setup compiler (ISCC.exe)
$isccCandidates = @(
    (Get-Command ISCC.exe -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Source),
    (Join-Path $env:LocalAppData 'Programs\Inno Setup 6\ISCC.exe'),
    "${env:ProgramFiles(x86)}\Inno Setup 6\ISCC.exe",
    "$env:ProgramFiles\Inno Setup 6\ISCC.exe"
)
$iscc = $isccCandidates | Where-Object { $_ -and (Test-Path -LiteralPath $_) } | Select-Object -First 1

if (-not $iscc) {
    Write-Status "Inno Setup not found in standard paths. Attempting install via winget..."
    try {
        winget install --id JRSoftware.InnoSetup -e --source winget --accept-source-agreements --accept-package-agreements --silent
    } catch {}
    $iscc = (Join-Path $env:LocalAppData 'Programs\Inno Setup 6\ISCC.exe')
    if (-not (Test-Path -LiteralPath $iscc)) {
        throw "Inno Setup compiler (ISCC.exe) not found. Please install Inno Setup 6."
    }
}
Write-Status "Using Inno Setup compiler: $iscc"

# 2. Compile Python source files
Write-Status "Compiling Python source files..."
Invoke-Checked $py @('-m', 'compileall', '-q', (Join-Path $Root 'windows/smartdoc'), (Join-Path $Root 'windows/app.py'), (Join-Path $Root 'windows/run_app.py'))

# 3. Build standalone folder with PyInstaller
$distDir = Join-Path $Root 'output/windows/dist'
$buildDir = Join-Path $Root 'output/windows/build_onedir'

Write-Status "Building fast uncompressed application directory with PyInstaller..."
Invoke-Checked $py @(
    '-m', 'PyInstaller',
    '--distpath', $distDir,
    '--workpath', $buildDir,
    '--noconfirm',
    $spec
)

$appExe = Join-Path $distDir 'SmartDocManager/SmartDocManager.exe'
Need-File $appExe
Write-Status "Built application folder: $(Split-Path $appExe)"

# 4. Compile Windows Setup Installer Wizard with Inno Setup
Write-Status "Compiling Windows Setup Wizard installer (SmartDocManager_Setup.exe)..."
$installerDir = Join-Path $Root 'output/windows/installer'
if (-not (Test-Path -LiteralPath $installerDir)) {
    New-Item -ItemType Directory -Path $installerDir -Force | Out-Null
}

Push-Location (Join-Path $Root 'windows')
try {
    & $iscc $iss
    if ($LASTEXITCODE -ne 0) {
        throw "Inno Setup compiler exited with error code $LASTEXITCODE"
    }
} finally {
    Pop-Location
}

$setupExe = Join-Path $installerDir 'SmartDocManager_Setup.exe'
Need-File $setupExe
$setupSizeMb = [math]::Round((Get-Item -LiteralPath $setupExe).Length / 1MB, 2)

Write-Host ""
Write-Host "==========================================================================" -ForegroundColor Green
Write-Host "SUCCESS: Windows Setup Wizard Installer Generated Successfully!" -ForegroundColor Green
Write-Host "Installer: $setupExe ($setupSizeMb MB)" -ForegroundColor Cyan
Write-Host "==========================================================================" -ForegroundColor Green
Write-Host ""
