param([string]$Root)
. "$PSScriptRoot/common/Helpers.ps1"
$py=Join-Path $Root 'windows/.venv/Scripts/python.exe';Need-File $py
Need-File (Join-Path $Root 'windows/app.py')
Invoke-Checked $py @('-m','compileall','-q',(Join-Path $Root 'windows'))
Invoke-Checked $py @('-c','import streamlit, PIL, pypdfium2, pandas')
Write-Status 'Windows Python application verified. Streamlit is not a standalone EXE.'
