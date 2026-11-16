param(
    [string]$Root,
    [int]$Port = 8501,
    [string]$Address = '127.0.0.1'
)
. "$PSScriptRoot/common/Helpers.ps1"
$Root = Assert-Root $Root
$py = Join-Path $Root 'windows/.venv/Scripts/python.exe'; Need-File $py
$launcher = Join-Path $Root 'windows/desktop_launcher.py'

$exe = Join-Path $Root 'output/windows/SmartDocManager.exe'
if (-not (Test-Path -LiteralPath $exe)) {
    $exe = Join-Path $Root 'output/windows/portable/SmartDocManager.exe'
}
if (Test-Path -LiteralPath $exe) {
    Write-Status "Launching standalone portable Windows application: $exe"
    Start-Process -FilePath $exe
} elseif (Test-Path -LiteralPath $launcher) {
    Write-Status "Launching SmartDocManager dedicated Windows desktop application window..."
    & $py $launcher $Port
} else {
    $app = Join-Path $Root 'windows/app.py'; Need-File $app
    $arguments = "-m streamlit run `"$app`" --server.address $Address --server.port $Port"
    Start-Process -FilePath $py -ArgumentList $arguments -WorkingDirectory (Join-Path $Root 'windows')
    Start-Sleep -Seconds 3
    Start-Process "http://${Address}:${Port}"
    Write-Status "Started local Streamlit process and browser at http://${Address}:${Port}."
}
