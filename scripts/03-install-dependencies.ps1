param([string]$Root)
. "$PSScriptRoot/common/Helpers.ps1"
$Root = Assert-Root $Root
Need-Command 'winget'
$packages=@(@('python','Python.Python.3.12'),@('java','EclipseAdoptium.Temurin.17.JDK'),@('git','Git.Git'))
foreach($entry in $packages){
 if(-not (Get-Command $entry[0] -ErrorAction SilentlyContinue)){
  $ok=Read-Host "Install $($entry[1]) using winget? (Y/N)"
  if($ok -notmatch '^[Yy]$'){throw "Dependency $($entry[0]) not approved"}
  Invoke-Checked 'winget' @('install','--id',$entry[1],'-e','--source','winget','--accept-package-agreements','--accept-source-agreements')
  throw 'Open a new PowerShell terminal to refresh PATH, then rerun Setup.'
 }
}
$venv=Join-Path $Root 'windows/.venv'
if(-not (Test-Path (Join-Path $venv 'Scripts/python.exe'))){Invoke-Checked 'python' @('-m','venv',$venv)}
$py=Join-Path $venv 'Scripts/python.exe'
Invoke-Checked $py @('-m','pip','install','-r',(Join-Path $Root 'windows/requirements.txt'))
Write-Status 'Python dependencies ready. Android SDK command-line tools setup is documented in README.md.'
