param(
    [string]$Root,
    [int]$Port = 8501,
    [string]$Address = 'localhost'
)
. "$PSScriptRoot/common/Helpers.ps1"
$Root = Assert-Root $Root
$py = Join-Path $Root 'windows/.venv/Scripts/python.exe'; Need-File $py
$app = Join-Path $Root 'windows/app.py'; Need-File $app
$arguments = "-m streamlit run `"$app`" --server.address $Address --server.port $Port"
Start-Process -FilePath $py -ArgumentList $arguments -WorkingDirectory (Join-Path $Root 'windows')
Start-Sleep -Seconds 3
Start-Process "http://${Address}:${Port}"
Write-Status "Started local Streamlit process and browser at http://${Address}:${Port}. Check browser for runtime errors."
