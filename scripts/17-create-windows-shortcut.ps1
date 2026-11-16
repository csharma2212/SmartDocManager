param([string]$Root)
. "$PSScriptRoot/common/Helpers.ps1"
$Root = Assert-Root $Root
$desktop=[Environment]::GetFolderPath('Desktop')
$shortcut=Join-Path $desktop 'SmartDocManager.lnk'
$shell=New-Object -ComObject WScript.Shell
$link=$shell.CreateShortcut($shortcut)
$exe = Join-Path $Root 'output/windows/SmartDocManager.exe'
if (-not (Test-Path -LiteralPath $exe)) {
    $exe = Join-Path $Root 'output/windows/portable/SmartDocManager.exe'
}
if (Test-Path -LiteralPath $exe) {
    $link.TargetPath = $exe
    $link.Arguments = ""
    $link.WorkingDirectory = (Split-Path $exe)
    $link.IconLocation = "$exe,0"
} else {
    $py = Join-Path $Root 'windows/.venv/Scripts/python.exe'
    $runApp = Join-Path $Root 'windows/run_app.py'
    $link.TargetPath = $py
    $link.Arguments = "`"$runApp`""
    $link.WorkingDirectory = (Join-Path $Root 'windows')
    $icon = Join-Path $Root 'windows/assets/icon.ico'
    if (Test-Path -LiteralPath $icon) { $link.IconLocation = "$icon,0" }
}
Write-Status "Created shortcut $shortcut"
