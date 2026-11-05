param([string]$Root)
. "$PSScriptRoot/common/Helpers.ps1"
$py=Join-Path $Root 'windows/.venv/Scripts/python.exe';Need-File $py
$app=Join-Path $Root 'windows/app.py';Need-File $app
$arguments="-m streamlit run `"$app`" --server.address 127.0.0.1 --server.port 8501"
Start-Process -FilePath $py -ArgumentList $arguments -WorkingDirectory (Join-Path $Root 'windows')
Start-Sleep -Seconds 3
Start-Process 'http://127.0.0.1:8501'
Write-Status 'Started local Streamlit process and browser. Check browser for runtime errors.'
