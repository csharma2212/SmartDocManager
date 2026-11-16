# SmartDocManager reproducible step runner for VS Code PowerShell 7+.
# Every invocation appends to the same diagnostics/build-session.log.
[CmdletBinding()]
param(
    [ValidateRange(1,8)][int]$Step,
    [switch]$ResetLog
)
$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
$logDir = Join-Path $root 'diagnostics'
$log = Join-Path $logDir 'build-session.log'
New-Item -ItemType Directory -Path $logDir -Force | Out-Null
if ($ResetLog) { Set-Content -LiteralPath $log -Value "SmartDocManager session reset: $(Get-Date -Format o)" -Encoding utf8 }
function Write-Log([string]$message) {
    $line = "[$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss.fff zzz')] $message"
    Add-Content -LiteralPath $log -Value $line -Encoding utf8
    Write-Host $line
}
function Run-Command([string]$exe, [string[]]$arguments, [string]$workingDir) {
    Write-Log "COMMAND: $exe $($arguments -join ' ')"
    Push-Location $workingDir
    try {
        # Native stdout and stderr are both captured; output is appended, never overwritten.
        & $exe @arguments 2>&1 | ForEach-Object { Write-Log ([string]$_) }
        $code = $LASTEXITCODE
        if ($null -eq $code) { $code = 0 }
        Write-Log "EXIT CODE: $code"
        if ($code -ne 0) { throw "Command failed with exit code ${code}: $exe" }
    } finally { Pop-Location }
}
function Ensure-AndroidSdk {
    # Auto-configure a machine-local SDK path before every Android Gradle invocation.
    # Never change the machine/user execution policy or persist environment variables.
    $candidates = [System.Collections.Generic.List[string]]::new()
    foreach ($v in @($env:ANDROID_HOME, $env:ANDROID_SDK_ROOT)) {
        if (-not [string]::IsNullOrWhiteSpace($v)) { $candidates.Add($v) }
    }
    $propertiesPath = Join-Path $androidDir 'local.properties'
    if (Test-Path -LiteralPath $propertiesPath -PathType Leaf) {
        $line = Get-Content -LiteralPath $propertiesPath | Where-Object { $_ -match '^\s*sdk\.dir\s*=' } | Select-Object -First 1
        if ($line) {
            $value = ($line -split '=', 2)[1].Trim().Replace('\\:', ':').Replace('\\\\', '\\')
            $candidates.Add($value)
        }
    }
    if ($env:LOCALAPPDATA) { $candidates.Add((Join-Path $env:LOCALAPPDATA 'Android\Sdk')) }
    if ($env:USERPROFILE) { $candidates.Add((Join-Path $env:USERPROFILE 'AppData\Local\Android\Sdk')) }
    foreach ($candidate in ($candidates | Select-Object -Unique)) {
        if ([string]::IsNullOrWhiteSpace($candidate)) { continue }
        $sdk = [System.IO.Path]::GetFullPath($candidate)
        if (-not (Test-Path -LiteralPath (Join-Path $sdk 'platforms') -PathType Container)) { continue }
        if (-not (Test-Path -LiteralPath (Join-Path $sdk 'platform-tools') -PathType Container)) {
            Write-Log "NOTICE: SDK candidate lacks platform-tools: $sdk"
        }
        $env:ANDROID_HOME = $sdk
        $env:ANDROID_SDK_ROOT = $sdk
        $safePath = $sdk.Replace('\', '/')
        $newLine = "sdk.dir=$safePath"
        $existing = @()
        if (Test-Path -LiteralPath $propertiesPath) { $existing = @(Get-Content -LiteralPath $propertiesPath) }
        $other = @($existing | Where-Object { $_ -notmatch '^\s*sdk\.dir\s*=' })
        # Preserve other properties; local.properties is machine-specific and must not be committed.
        @($other + $newLine) | Set-Content -LiteralPath $propertiesPath -Encoding utf8
        Write-Log "ANDROID SDK CONFIGURED: $sdk; wrote android/local.properties"
        return
    }
    throw 'Android SDK not found. Install/configure Android Studio SDK or set ANDROID_HOME, then rerun this step. No Gradle dependencies were changed.'
}

function Need-Command([string]$name) {
    $found = Get-Command $name -ErrorAction SilentlyContinue
    if (-not $found) { throw "Required command not found: $name" }
    Write-Log "FOUND: $name -> $($found.Source)"
}
function Need-File([string]$path) {
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw "Required file not found: $path" }
}
$venvPython = Join-Path $root 'windows/.venv/Scripts/python.exe'
$winDir = Join-Path $root 'windows'
$androidDir = Join-Path $root 'android'
$cloudDir = Join-Path $root 'hosted-backend/cloudflare'
$labels = @{
    1 = 'Check tools and project structure'
    2 = 'Create isolated Windows Python environment'
    3 = 'Install and validate Windows Python dependencies'
    4 = 'Run Windows automated tests'
    5 = 'Run Cloudflare Worker automated tests'
    6 = 'Run Android unit tests'
    7 = 'Build Android debug and release APKs'
    8 = 'Launch Windows Streamlit application'
}
Write-Log ('=' * 72)
Write-Log "START STEP $Step - $($labels[$Step]); PowerShell=$($PSVersionTable.PSVersion); Root=$root"
try {
    if ($PSVersionTable.PSVersion.Major -lt 7) { throw 'PowerShell 7 or newer (pwsh) is required.' }
    switch ($Step) {
        1 {
            Need-Command 'python'
            Need-Command 'java'
            Need-Command 'node'
            Need-Command 'npm'
            Need-File (Join-Path $winDir 'requirements.txt')
            Need-File (Join-Path $winDir 'app.py')
            Need-File (Join-Path $winDir 'desktop_launcher.py')
            Need-File (Join-Path $androidDir 'gradlew.bat')
            Need-File (Join-Path $androidDir 'gradle/wrapper/gradle-wrapper.jar')
            Need-File (Join-Path $cloudDir 'src/worker.mjs')
            Run-Command 'python' @('--version') $root
            Run-Command 'java' @('-version') $root
            Run-Command 'node' @('--version') $root
            Run-Command 'npm.cmd' @('--version') $root
            Ensure-AndroidSdk
        }
        2 {
            Need-Command 'python'
            if (Test-Path $venvPython) { Write-Log 'Existing .venv retained; no recreation.' }
            else { Run-Command 'python' @('-m','venv','.venv') $winDir }
            Need-File $venvPython
        }
        3 {
            Need-File $venvPython
            Run-Command $venvPython @('-m','pip','install','-r','requirements.txt') $winDir
            Run-Command $venvPython @('-m','pip','check') $winDir
        }
        4 {
            Need-File $venvPython
            Run-Command $venvPython @('-m','pytest','-q','tests') $winDir
        }
        5 {
            Need-Command 'node'
            Run-Command 'npm.cmd' @('test') $cloudDir
        }
        6 {
            Need-Command 'java'
            Need-File (Join-Path $androidDir 'gradlew.bat')
            Ensure-AndroidSdk
            Run-Command (Join-Path $androidDir 'gradlew.bat') @('--no-daemon','testDebugUnitTest','--stacktrace') $androidDir
        }
        7 {
            Need-Command 'java'
            Ensure-AndroidSdk

            # Load release keystore environment variables from User scope if not present in current process
            foreach ($var in @('SMARTDOC_KEYSTORE', 'SMARTDOC_STORE_PASSWORD', 'SMARTDOC_KEY_ALIAS', 'SMARTDOC_KEY_PASSWORD')) {
                if (-not [Environment]::GetEnvironmentVariable($var, 'Process')) {
                    $userVal = [Environment]::GetEnvironmentVariable($var, 'User')
                    if ($userVal) {
                        [Environment]::SetEnvironmentVariable($var, $userVal, 'Process')
                        Set-Item -Path "env:$var" -Value $userVal
                    }
                }
            }

            Write-Log 'Building Android debug and release APKs...'
            Run-Command (Join-Path $androidDir 'gradlew.bat') @('--no-daemon','assembleDebug','assembleRelease','--stacktrace') $androidDir

            # 1. Process Debug APK
            $candidatesDebug = @(
                (Join-Path $androidDir 'app/build/outputs/apk/debug/app-debug.apk')
            )
            if ($env:LOCALAPPDATA) { $candidatesDebug += (Join-Path $env:LOCALAPPDATA 'AndroidDev/SmartDocManager-build/app/outputs/apk/debug/app-debug.apk') }
            $apkDebug = $candidatesDebug | Where-Object { Test-Path $_ -PathType Leaf } | Select-Object -First 1
            if (-not $apkDebug) { throw 'Gradle returned success, but debug APK was not found at expected locations.' }
            $destDebug = Join-Path $root 'output/android/SmartDocManager-debug.apk'
            New-Item -ItemType Directory -Force -Path (Split-Path $destDebug) | Out-Null
            Copy-Item -LiteralPath $apkDebug -Destination $destDebug -Force
            Write-Log "DEBUG APK CREATED: $destDebug"

            # 2. Process Release APK (signed or unsigned)
            $candidatesRelease = @(
                (Join-Path $androidDir 'app/build/outputs/apk/release/app-release.apk'),
                (Join-Path $androidDir 'app/build/outputs/apk/release/app-release-unsigned.apk')
            )
            if ($env:LOCALAPPDATA) {
                $candidatesRelease += (Join-Path $env:LOCALAPPDATA 'AndroidDev/SmartDocManager-build/app/outputs/apk/release/app-release.apk')
                $candidatesRelease += (Join-Path $env:LOCALAPPDATA 'AndroidDev/SmartDocManager-build/app/outputs/apk/release/app-release-unsigned.apk')
            }
            $apkRelease = $candidatesRelease | Where-Object { Test-Path $_ -PathType Leaf } | Select-Object -First 1
            if (-not $apkRelease) { throw 'Gradle returned success, but release APK was not found at expected locations.' }
            $destRelease = Join-Path $root 'output/android/SmartDocManager-release.apk'
            Copy-Item -LiteralPath $apkRelease -Destination $destRelease -Force
            Write-Log "RELEASE APK CREATED: $destRelease"
        }
        8 {
            Need-File $venvPython
            $launcher = Join-Path $winDir 'desktop_launcher.py'
            if (Test-Path $launcher) {
                Write-Log 'Starting SmartDocManager Desktop Application window...'
                Run-Command $venvPython @('desktop_launcher.py') $winDir
            } else {
                Write-Log 'Starting Streamlit. Press Ctrl+C to stop; this step stays active while the app runs.'
                Run-Command $venvPython @('-m','streamlit','run','app.py','--server.address','127.0.0.1','--server.port','8501') $winDir
            }
        }
    }
    Write-Log "PASS STEP $Step - $($labels[$Step])"
} catch {
    Write-Log "FAIL STEP $Step - $($labels[$Step])"
    Write-Log "ERROR: $($_.Exception.Message)"
    Write-Log "LOCATION: $($_.ScriptStackTrace)"
    Write-Host "Share ONLY this file for troubleshooting: $log" -ForegroundColor Yellow
    exit 1
}
Write-Log "END STEP $Step"
