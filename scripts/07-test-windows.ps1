param([string]$Root)
. "$PSScriptRoot/common/Helpers.ps1"
$py=Join-Path $Root 'windows/.venv/Scripts/python.exe'
Need-File $py
Need-File (Join-Path $Root 'windows/app.py')
Need-File (Join-Path $Root 'windows/requirements.txt')
$testDir=Join-Path $Root 'windows/tests'
if(-not (Get-ChildItem -Path $testDir -Filter 'test_*.py' -File -ErrorAction SilentlyContinue)){throw 'No Windows tests found: add test_*.py'}
$outDir=Join-Path $Root 'output/windows'; New-Item -ItemType Directory -Force -Path $outDir | Out-Null
$xmlOut=Join-Path $outDir 'test-results.xml'
& $py -m pytest -v --junitxml=$xmlOut $testDir
Show-TestBlock "WINDOWS TEST RESULTS" @($xmlOut)
