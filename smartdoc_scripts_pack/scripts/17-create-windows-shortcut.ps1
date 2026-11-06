param([string]$Root)
. "$PSScriptRoot/common/Helpers.ps1"
$Root = Assert-Root $Root
$desktop=[Environment]::GetFolderPath('Desktop')
$shortcut=Join-Path $desktop 'SmartDocManager.lnk'
$shell=New-Object -ComObject WScript.Shell
$link=$shell.CreateShortcut($shortcut)
$link.TargetPath='powershell.exe'
$link.Arguments="-NoProfile -ExecutionPolicy Bypass -File `"$(Join-Path $Root 'dev.ps1')`" -Action LaunchWindows"
$icon=Join-Path $Root 'windows/assets/icon.ico'
if (Test-Path -LiteralPath $icon) { $link.IconLocation = "$icon,0" }
$link.WorkingDirectory=$Root;$link.Save()
Write-Status "Created shortcut $shortcut"
