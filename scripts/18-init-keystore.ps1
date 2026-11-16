param(
    [string]$Root = $PSScriptRoot
)
. "$PSScriptRoot/common/Helpers.ps1"
$Root = Assert-Root $Root
& powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot '20-setup-signing-and-sha1.ps1') -ProjectRoot $Root
