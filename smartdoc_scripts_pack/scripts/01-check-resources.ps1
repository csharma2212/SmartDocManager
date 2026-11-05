param([string]$Root)
. "$PSScriptRoot/common/Helpers.ps1"
while ($true) {
 $os=Get-CimInstance Win32_OperatingSystem
 $drive=Get-PSDrive -Name ([IO.Path]::GetPathRoot($Root).Substring(0,1))
 $freeGb=[math]::Round($drive.Free/1GB,2);$availMb=[math]::Round($os.FreePhysicalMemory/1024)
 Write-Status "Available storage: $freeGb GB; available RAM: $availMb MB"
 if($freeGb -ge 10 -and $availMb -ge 1024){exit 0}
 Write-Warning 'This app requires minimum space and memory for limited use'
 Write-Warning 'Free storage or close unused apps. No apps will be terminated automatically.'
 $choice=Read-Host 'Press R to recheck, C to continue with warning, or X to cancel'
 if($choice -match '^[Rr]$'){continue}
 if($choice -match '^[Cc]$'){exit 0}
 if($choice -match '^[Xx]$'){exit 2}
}
