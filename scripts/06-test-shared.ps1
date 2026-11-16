param([string]$Root)
. "$PSScriptRoot/common/Helpers.ps1"
$Root = Assert-Root $Root
$taxonomy = Get-Content -Raw -LiteralPath (Join-Path $Root 'shared-spec/taxonomy.json') | ConvertFrom-Json
if (-not $taxonomy.categories -or $taxonomy.categories.Count -lt 20) {
    throw 'Taxonomy contract mismatch: expected full taxonomy categories'
}
foreach ($requiredCat in @('Aadhaar Card', 'Medical Prescription')) {
    if ($taxonomy.categories -notcontains $requiredCat) {
        throw "Taxonomy contract missing required category: $requiredCat"
    }
}
if ($taxonomy.categories -notcontains 'Permanent Account Number Card' -and $taxonomy.categories -notcontains 'PAN Card') {
    throw "Taxonomy contract missing PAN Card category"
}
if ($taxonomy.categories -notcontains 'Driving License' -and $taxonomy.categories -notcontains 'Driving Licence') {
    throw "Taxonomy contract missing Driving License category"
}
Need-File (Join-Path $Root 'shared-spec/result-schema.json')
Need-File (Join-Path $Root 'shared-spec/test-fixtures/manifest.json')
Write-Status 'Shared contract files exist; application contract tests must be added before accepting a completed build.'
