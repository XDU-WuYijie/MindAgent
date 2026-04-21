param(
    [string]$ApiBaseUrl = "http://localhost:8081",
    [string]$VllmBaseUrl = "http://localhost:8000",
    [string]$Model = "Qwen/Qwen2.5-14B-Instruct",
    [int]$StartupTimeoutSec = 90
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = (Resolve-Path "$PSScriptRoot\..").Path
$backendDir = Join-Path $repoRoot "backend"
$logDir = Join-Path $repoRoot "scripts\.logs"
$logFile = Join-Path $logDir "backend-chroma.log"
$errFile = Join-Path $logDir "backend-chroma.err.log"

if (-not (Test-Path $logDir)) {
    New-Item -Path $logDir -ItemType Directory | Out-Null
}

function Write-Step {
    param([string]$Message)
    Write-Host ""
    Write-Host "==> $Message" -ForegroundColor Cyan
}

function Wait-ApiReady {
    param(
        [string]$HealthUrl,
        [int]$TimeoutSec
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    while ((Get-Date) -lt $deadline) {
        try {
            $resp = Invoke-WebRequest -Uri $HealthUrl -TimeoutSec 5 -UseBasicParsing
            if ($resp.StatusCode -eq 200 -and $resp.Content -match "ok") {
                return $true
            }
        } catch {
            Start-Sleep -Milliseconds 500
        }
    }
    return $false
}

function Stop-JavaOnApiPort {
    param([string]$ApiUrl)

    $uri = [System.Uri]$ApiUrl
    $port = $uri.Port
    $listeners = @(Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue)
    foreach ($listener in $listeners) {
        $proc = Get-Process -Id $listener.OwningProcess -ErrorAction SilentlyContinue
        if ($null -eq $proc) {
            continue
        }
        if ($proc.ProcessName -eq "java" -or $proc.ProcessName -eq "javaw") {
            Stop-Process -Id $listener.OwningProcess -Force
            Write-Host "Stopped Java process PID=$($listener.OwningProcess) on port $port"
        }
    }
}

Write-Step "1) Reset dev env and prepare Chroma resources"
powershell -ExecutionPolicy Bypass -File (Join-Path $repoRoot "scripts\reset-dev-env.ps1")

Write-Step "2) Start backend in Chroma profile"
if (Test-Path $logFile) {
    Remove-Item $logFile -Force
}
if (Test-Path $errFile) {
    Remove-Item $errFile -Force
}

$startCmd = "Set-Location '$backendDir'; `$env:SPRING_PROFILES_ACTIVE='chroma'; mvn spring-boot:run"
$proc = Start-Process -FilePath "powershell" `
    -ArgumentList @("-NoProfile", "-Command", $startCmd) `
    -RedirectStandardOutput $logFile `
    -RedirectStandardError $errFile `
    -PassThru

try {
    $ok = Wait-ApiReady -HealthUrl "$ApiBaseUrl/api/health" -TimeoutSec $StartupTimeoutSec
    if (-not $ok) {
        Write-Host "Backend startup timed out. Last log lines:" -ForegroundColor Red
        if (Test-Path $logFile) {
            Get-Content $logFile -Tail 80
        }
        if (Test-Path $errFile) {
            Get-Content $errFile -Tail 80
        }
        throw "Backend failed to start in Chroma profile."
    }

    Write-Step "3) Run smoke test"
    powershell -ExecutionPolicy Bypass -File (Join-Path $repoRoot "scripts\smoke-test.ps1") `
        -ApiBaseUrl $ApiBaseUrl `
        -VllmBaseUrl $VllmBaseUrl `
        -Model $Model
} finally {
    Write-Step "4) Stop backend process"
    if ($null -ne $proc -and -not $proc.HasExited) {
        Stop-Process -Id $proc.Id -Force
        Write-Host "Stopped backend process PID=$($proc.Id)"
    }
    Stop-JavaOnApiPort -ApiUrl $ApiBaseUrl
}

Write-Host ""
Write-Host "Chroma smoke test finished." -ForegroundColor Green
