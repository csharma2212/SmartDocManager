param([string]$Root)
. "$PSScriptRoot/common/Helpers.ps1"
$Root = Assert-Root $Root
$taxonomy=Get-Content -Raw -LiteralPath (Join-Path $Root 'shared-spec/taxonomy.json')|ConvertFrom-Json
$expected=@('Medical Record','Government-issued Document','Organization-issued ID Card','Legal Document','uncategorizable input')
if(($taxonomy.categories -join '|') -ne ($expected -join '|')){throw 'Taxonomy contract mismatch'}
Need-File (Join-Path $Root 'shared-spec/result-schema.json')
Need-File (Join-Path $Root 'shared-spec/test-fixtures/manifest.json')
Write-Status 'Shared contract files exist; application contract tests must be added before accepting a completed build.'
