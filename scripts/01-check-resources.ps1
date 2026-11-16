param([string]$Root)
. "$PSScriptRoot/common/Helpers.ps1"
$Root = Assert-Root $Root
while ($true) {
    $os = try { Get-CimInstance Win32_OperatingSystem -ErrorAction Stop } catch { $null }
    $drive = try { Get-PSDrive -Name ([IO.Path]::GetPathRoot($Root).Substring(0,1)) -ErrorAction SilentlyContinue } catch { $null }
    
    $freeGb = if ($drive -and $drive.Free) { [math]::Round($drive.Free/1GB, 2) } else { 50.0 }
    $availMb = if ($os -and $os.FreePhysicalMemory) { [math]::Round($os.FreePhysicalMemory/1024) } else { 4096 }

    Write-Status "Available storage: $freeGb GB; available RAM: $availMb MB"
    if ($freeGb -ge 10 -and $availMb -ge 1024) { exit 0 }
    Write-Warning 'This app requires minimum space and memory for limited use'
    Write-Warning 'Free storage or close unused apps. No apps will be terminated automatically.'
    if (-not [Environment]::UserInteractive -or [Console]::IsInputRedirected) { exit 0 }
    $choice = Read-Host 'Press R to recheck, C to continue with warning, or X to cancel'
    if ($choice -match '^[Rr]$') { continue }
    if ($choice -match '^[Cc]$') { exit 0 }
    if ($choice -match '^[Xx]$') { exit 2 }
}
