Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
function Assert-Root([string]$Root) {
 if (-not (Test-Path -LiteralPath $Root -PathType Container)) { throw "Project root does not exist: $Root" }
 return (Resolve-Path -LiteralPath $Root).Path
}
function Need-File([string]$Path) { if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) { throw "Required file missing: $Path" } }
function Need-Command([string]$Name) { if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) { throw "Missing tool: $Name. Run Setup / Repair first." } }
function Invoke-Checked([string]$Exe,[string[]]$Arguments) {
 & $Exe @Arguments
 if ($LASTEXITCODE -ne 0) { throw "Command failed (exit $LASTEXITCODE): $Exe $($Arguments -join ' ')" }
}
function Write-Status([string]$Message) { Write-Host "[SmartDoc] $Message" }
function Backup-IfChanged([string]$Path,[string]$Content,[string]$Root) {
 if (Test-Path -LiteralPath $Path) {
  $existing = [IO.File]::ReadAllText($Path)
  if ($existing -eq $Content) { return }
  $relative = $Path.Substring($Root.TrimEnd("\").Length).TrimStart("\")
  $backup = Join-Path $Root ('backups/' + (Get-Date -Format 'yyyyMMdd-HHmmssfff') + '/' + $relative)
  New-Item -ItemType Directory -Force -Path (Split-Path $backup) | Out-Null
  Copy-Item -LiteralPath $Path -Destination $backup -Force
  Write-Status "Backed up $relative"
 }
 [IO.File]::WriteAllText($Path,$Content,[Text.UTF8Encoding]::new($false))
}
function Resolve-AndroidSdk([string]$Root) {
 $sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { $env:ANDROID_SDK_ROOT }
 if (-not $sdk -and $Root) {
  $localProp = Join-Path $Root 'android/local.properties'
  if (Test-Path -LiteralPath $localProp) {
   $line = (Get-Content $localProp | Where-Object { $_ -match '^\s*sdk\.dir\s*=\s*(.+)$' })
   if ($line -match '^\s*sdk\.dir\s*=\s*(.+)$') {
    $cand = $Matches[1].Trim().Replace('/', '\')
    $exists = $false
    try { $exists = (Test-Path -LiteralPath $cand) } catch { $exists = $true }
    if ($exists) { $sdk = $cand }
   }
  }
 }
 if (-not $sdk) {
  $defaultSdk = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
  $exists = $false
  try { $exists = (Test-Path -LiteralPath $defaultSdk) } catch {}
  if ($exists) { $sdk = $defaultSdk }
 }
 if ($sdk) {
  $env:ANDROID_HOME = $sdk
  $env:ANDROID_SDK_ROOT = $sdk
 }
 return $sdk
}
function Show-TestBlock([string]$Title, [string[]]$XmlFiles) {
 Write-Host ""
 Write-Host ("=" * 70) -ForegroundColor Cyan
 Write-Host ("  $Title") -ForegroundColor Cyan
 Write-Host ("=" * 70) -ForegroundColor Cyan
 $total = 0; $passed = 0; $failed = 0; $skipped = 0
 foreach ($xmlPath in $XmlFiles) {
  if (-not (Test-Path -LiteralPath $xmlPath)) { continue }
  [xml]$doc = Get-Content -LiteralPath $xmlPath
  foreach ($case in $doc.SelectNodes('//testcase')) {
   $total++
   $name = $case.name
   $rawClass = $case.classname
   $class = ($rawClass -split '\.')[-1]
   $timeVal = if ($case.time) { [math]::Round([double]$case.time, 3) } else { 0 }
   $failNode = $case.SelectSingleNode('failure')
   $errNode = $case.SelectSingleNode('error')
   $skipNode = $case.SelectSingleNode('skipped')
   if ($failNode -ne $null -or $errNode -ne $null) {
    $failed++
    Write-Host ("  [FAIL] $class :: $name (${timeVal}s)") -ForegroundColor Red
   } elseif ($skipNode -ne $null) {
    $skipped++
    Write-Host ("  [SKIP] $class :: $name") -ForegroundColor Yellow
   } else {
    $passed++
    Write-Host ("  [PASS] $class :: $name (${timeVal}s)") -ForegroundColor Green
   }
  }
 }
 Write-Host ("-" * 70) -ForegroundColor Gray
 $summaryColor = if ($failed -gt 0 -or $total -eq 0) { "Red" } else { "Green" }
 Write-Host "  Final Results: $total Executed | $passed Passed | $failed Failed | $skipped Skipped" -ForegroundColor $summaryColor
 Write-Host ("=" * 70) -ForegroundColor Cyan
 Write-Host ""
 if ($failed -gt 0) { throw "$Title failed: $failed test(s) failed." }
 if ($total -eq 0) { throw "$Title failed: No test cases found." }
}
