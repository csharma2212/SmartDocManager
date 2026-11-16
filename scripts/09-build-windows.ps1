param([string]$Root)
. "$PSScriptRoot/common/Helpers.ps1"
$Root = Assert-Root $Root
$py = Join-Path $Root 'windows/.venv/Scripts/python.exe'; Need-File $py
$pyinstaller = Join-Path $Root 'windows/.venv/Scripts/pyinstaller.exe'
if (-not (Test-Path -LiteralPath $pyinstaller)) {
    Write-Status "Installing PyInstaller..."
    Invoke-Checked $py @('-m', 'pip', 'install', 'pyinstaller>=6.0')
}

Need-File (Join-Path $Root 'windows/app.py')
Need-File (Join-Path $Root 'windows/run_app.py')
$spec = Join-Path $Root 'windows/SmartDocManager.spec'; Need-File $spec

Write-Status "Compiling Python source files..."
Invoke-Checked $py @('-m', 'compileall', '-q', (Join-Path $Root 'windows/smartdoc'), (Join-Path $Root 'windows/app.py'), (Join-Path $Root 'windows/run_app.py'))

$distDir = Join-Path $Root 'output/windows/portable'
$buildDir = Join-Path $Root 'output/windows/build_onefile'

Write-Status "Building single-file standalone portable executable with PyInstaller..."
Invoke-Checked $pyinstaller @(
    '--distpath', $distDir,
    '--workpath', $buildDir,
    '--noconfirm',
    $spec
)

$sourceExe = Join-Path $distDir 'SmartDocManager.exe'
Need-File $sourceExe

$finalExe = Join-Path $Root 'output/windows/SmartDocManager.exe'
Get-Process -Name SmartDocManager -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue
Start-Sleep -Milliseconds 300
Copy-Item -LiteralPath $sourceExe -Destination $finalExe -Force
$exeSizeMb = [math]::Round((Get-Item -LiteralPath $finalExe).Length / 1MB, 2)
Write-Status "Generated standalone portable single-file executable: $finalExe ($exeSizeMb MB)"

# Create a clean portable distribution zip containing the single executable
$portableZip = Join-Path $Root 'output/windows/SmartDocManager-windows-portable.zip'
Remove-Item -Force $portableZip -ErrorAction SilentlyContinue
Write-Status "Archiving portable package to $portableZip..."
$archived = $false
for ($i = 0; $i -lt 5; $i++) {
    try {
        Compress-Archive -LiteralPath $sourceExe -DestinationPath $portableZip -Force -ErrorAction Stop
        $archived = $true
        break
    } catch {
        Start-Sleep -Seconds 1
    }
}
if (-not $archived) {
    Compress-Archive -LiteralPath $finalExe -DestinationPath $portableZip -Force
}

Write-Status "Windows portable standalone build completed successfully."
