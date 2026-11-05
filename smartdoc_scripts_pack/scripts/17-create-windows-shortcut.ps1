param([string]$Root)
. "$PSScriptRoot/common/Helpers.ps1"
$desktop=[Environment]::GetFolderPath('Desktop')
$shortcut=Join-Path $desktop 'SmartDoc POC.lnk'
$shell=New-Object -ComObject WScript.Shell
$link=$shell.CreateShortcut($shortcut)
$link.TargetPath='powershell.exe'
$link.Arguments="-NoProfile -ExecutionPolicy Bypass -File `"$(Join-Path $Root 'dev.ps1')`" -Action LaunchWindows"
$link.WorkingDirectory=$Root;$link.Save()
Write-Status "Created shortcut $shortcut"
