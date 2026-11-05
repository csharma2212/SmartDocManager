param([string]$Root)
. "$PSScriptRoot/common/Helpers.ps1"
$Root=Assert-Root $Root
$dirs=@('docs','shared-spec/test-fixtures','windows/smartdoc','windows/tests','android','evaluation/results','output/android','output/windows','logs','backups')
foreach($dir in $dirs){New-Item -ItemType Directory -Force -Path (Join-Path $Root $dir)|Out-Null}
$tax=@{categories=@('Medical Record','Government-issued Document','Organization-issued ID Card','Legal Document','uncategorizable input')}|ConvertTo-Json -Depth 3
Backup-IfChanged (Join-Path $Root 'shared-spec/taxonomy.json') ($tax+"`n") $Root
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
