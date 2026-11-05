param([Parameter(Mandatory=$true)][string]$ProjectRoot)
$ErrorActionPreference='Stop'
$source=$PSScriptRoot
if($ProjectRoot -eq './' -or $ProjectRoot -eq '.'){$ProjectRoot=(Get-Location).Path}
if(-not (Test-Path -LiteralPath $ProjectRoot -PathType Container)){throw 'Select an EXISTING folder.'}
$target=(Resolve-Path -LiteralPath $ProjectRoot).Path
$items=@('dev.ps1','scripts','README.md')
foreach($item in $items){
 $src=Join-Path $source $item;$dst=Join-Path $target $item
 if([IO.Path]::GetFullPath($src) -eq [IO.Path]::GetFullPath($dst)){continue}
 if(Test-Path -LiteralPath $dst){
  $backup=Join-Path $target ('backups/bootstrap-'+(Get-Date -Format 'yyyyMMdd-HHmmssfff')+'/'+$item)
  New-Item -ItemType Directory -Force -Path (Split-Path $backup)|Out-Null
  Copy-Item -LiteralPath $dst -Destination $backup -Recurse -Force
 }
 Copy-Item -LiteralPath $src -Destination $dst -Recurse -Force
}
Write-Host "SmartDoc scripts installed in: $target"
Write-Host 'Next: powershell -NoProfile -ExecutionPolicy Bypass -File .\dev.ps1'
