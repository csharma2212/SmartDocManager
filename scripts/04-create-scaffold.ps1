param([string]$Root)
. "$PSScriptRoot/common/Helpers.ps1"
$Root=Assert-Root $Root
$dirs=@('docs','shared-spec/test-fixtures','windows/smartdoc','windows/tests','windows/assets','android','evaluation/results','output/android','output/windows','logs','backups')
foreach($dir in $dirs){New-Item -ItemType Directory -Force -Path (Join-Path $Root $dir)|Out-Null}
$fullTax = @(
    'Aadhaar Card','PAN Card','Passport','Voter ID Card','Driving Licence',
    'Form 16 / TDS Certificate','Form 26AS / Tax Statement','Tax Form (Form 13 / 15G / Lower TDS)',
    'Income Tax Return','EPF / Provident Fund (Form 13 / UAN)','Rental / Lease Agreement',
    'Legal / Contract Document','Bank Statement','Credit Card Statement',
    'Mutual Fund / Investment Statement','Medical Prescription','Medical Diagnostic / Lab Report',
    'Electricity Bill','Telecom / Internet Bill','Gas / LPG Bill','Vehicle Registration / Insurance',
    'Salary Slip / Payslip','Educational Document','Other Document','Medical Record',
    'Government-issued Document','Organization-issued ID Card','Legal Document','Tax Document',
    'Investment Statement','Bill or Invoice','Payment Receipt','Insurance Document',
    'Vehicle Document','Employment Document','Property or Rental Document',
    'Utility or Telecom Document','Travel Document','uncategorizable input'
)
$tax = @{categories = $fullTax} | ConvertTo-Json -Depth 3
Backup-IfChanged (Join-Path $Root 'shared-spec/taxonomy.json') ($tax+"`n") $Root
$appConfig = Join-Path $Root 'shared-spec/app-config.json'
if (-not (Test-Path -LiteralPath $appConfig -PathType Leaf)) {
    $defaultCfg = @{
        backend_url = "https://smartdoc-classifier.csharma2212.workers.dev"
        google_desktop_client_id = ""
        google_web_client_id = ""
    } | ConvertTo-Json -Depth 3
    [IO.File]::WriteAllText($appConfig, $defaultCfg + "`n", [Text.UTF8Encoding]::new($false))
    Write-Status "Initialized shared-spec/app-config.json template"
}
$localProp = Join-Path $Root 'android/local.properties'
if (-not (Test-Path -LiteralPath $localProp -PathType Leaf)) {
    $sdk = Resolve-AndroidSdk $Root
    if ($sdk) {
        $sdkDir = $sdk.Replace('\', '/')
        [IO.File]::WriteAllText($localProp, "sdk.dir=$sdkDir`n", [Text.UTF8Encoding]::new($false))
        Write-Status "Initialized android/local.properties with SDK location: $sdkDir"
    }
}
Write-Status 'Created non-destructive project directories and taxonomy. Application source is bundled; Gradle wrapper generation remains required.'
