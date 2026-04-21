param(
    [int]$ApiPort = 8081,
    [int]$ChromaPort = 18001,
    [string]$ChromaContainer = "chroma",
    [string]$ChromaHost = "127.0.0.1",
    [string]$ChromaTenant = "SpringAiTenant",
    [string]$ChromaDatabase = "SpringAiDatabase",
    [string]$ChromaCollection = "mindbridge_knowledge"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Write-Step {
    param([string]$Message)
    Write-Host ""
    Write-Host "==> $Message" -ForegroundColor Cyan
}

function Stop-JavaOnPort {
    param([int]$Port)

    $listeners = @(Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue)
    if ($listeners.Count -eq 0) {
        Write-Host "No listener found on port $Port"
        return
    }

    $pidList = $listeners | Select-Object -ExpandProperty OwningProcess -Unique
    foreach ($procId in $pidList) {
        $proc = Get-Process -Id $procId -ErrorAction SilentlyContinue
        if ($null -eq $proc) {
            continue
        }

        $name = $proc.ProcessName.ToLowerInvariant()
        if ($name -eq "java" -or $name -eq "javaw") {
            Stop-Process -Id $procId -Force
            Write-Host "Stopped Java process PID=$procId on port $Port"
        } else {
            Write-Host "Skip PID=$procId ($($proc.ProcessName)) on port $Port"
        }
    }
}

function Recreate-ChromaContainer {
    param(
        [string]$ContainerName,
        [int]$Port
    )

    $existing = docker ps -a --filter "name=^/${ContainerName}$" --format "{{.Names}}"
    if ($existing -match "^$ContainerName$") {
        docker rm -f $ContainerName | Out-Null
        Write-Host "Removed old container: $ContainerName"
    }

    docker run -d --name $ContainerName -p "${Port}:8000" chromadb/chroma:latest | Out-Null
    Write-Host "Started $ContainerName on localhost:$Port -> container:8000"
}

function Invoke-ChromaApi {
    param(
        [ValidateSet("GET", "POST")]
        [string]$Method,
        [string]$Uri,
        [hashtable]$Body = $null
    )

    if ($null -eq $Body) {
        return Invoke-RestMethod -Method $Method -Uri $Uri -TimeoutSec 10
    }

    $json = $Body | ConvertTo-Json -Compress
    return Invoke-RestMethod -Method $Method -Uri $Uri -ContentType "application/json" -Body $json -TimeoutSec 10
}

function Wait-ChromaReady {
    param(
        [string]$BaseUrl,
        [int]$Retry = 30
    )

    for ($i = 1; $i -le $Retry; $i++) {
        try {
            $resp = Invoke-WebRequest -Uri "$BaseUrl/openapi.json" -TimeoutSec 5 -UseBasicParsing
            if ($resp.StatusCode -eq 200) {
                Write-Host "Chroma is ready"
                return
            }
        } catch {
            Start-Sleep -Milliseconds 500
        }
    }
    throw "Chroma is not ready after retries."
}

function Ensure-ChromaTenant {
    param(
        [string]$BaseUrl,
        [string]$Tenant
    )

    try {
        $null = Invoke-ChromaApi -Method GET -Uri "$BaseUrl/api/v2/tenants/$Tenant"
        Write-Host "Tenant exists: $Tenant"
    } catch {
        $null = Invoke-ChromaApi -Method POST -Uri "$BaseUrl/api/v2/tenants" -Body @{ name = $Tenant }
        Write-Host "Tenant created: $Tenant"
    }
}

function Ensure-ChromaDatabase {
    param(
        [string]$BaseUrl,
        [string]$Tenant,
        [string]$Database
    )

    try {
        $null = Invoke-ChromaApi -Method GET -Uri "$BaseUrl/api/v2/tenants/$Tenant/databases/$Database"
        Write-Host "Database exists: $Database"
    } catch {
        $null = Invoke-ChromaApi -Method POST -Uri "$BaseUrl/api/v2/tenants/$Tenant/databases" -Body @{ name = $Database }
        Write-Host "Database created: $Database"
    }
}

function Ensure-ChromaCollection {
    param(
        [string]$BaseUrl,
        [string]$Tenant,
        [string]$Database,
        [string]$Collection
    )

    try {
        $null = Invoke-ChromaApi -Method GET -Uri "$BaseUrl/api/v2/tenants/$Tenant/databases/$Database/collections/$Collection"
        Write-Host "Collection exists: $Collection"
    } catch {
        $null = Invoke-ChromaApi -Method POST -Uri "$BaseUrl/api/v2/tenants/$Tenant/databases/$Database/collections" -Body @{ name = $Collection }
        Write-Host "Collection created: $Collection"
    }
}

$chromaBaseUrl = "http://${ChromaHost}:${ChromaPort}"

Write-Host "MindBridge dev environment reset starting..." -ForegroundColor Yellow
Write-Step "1) Clear backend port ($ApiPort) Java residue"
Stop-JavaOnPort -Port $ApiPort

Write-Step "2) Recreate Chroma container on port $ChromaPort"
Recreate-ChromaContainer -ContainerName $ChromaContainer -Port $ChromaPort

Write-Step "3) Wait for Chroma ready"
Wait-ChromaReady -BaseUrl $chromaBaseUrl

Write-Step "4) Ensure Chroma resources"
Ensure-ChromaTenant -BaseUrl $chromaBaseUrl -Tenant $ChromaTenant
Ensure-ChromaDatabase -BaseUrl $chromaBaseUrl -Tenant $ChromaTenant -Database $ChromaDatabase
Ensure-ChromaCollection -BaseUrl $chromaBaseUrl -Tenant $ChromaTenant -Database $ChromaDatabase -Collection $ChromaCollection

Write-Step "5) Quick checks"
$portCheck = @(Get-NetTCPConnection -State Listen -LocalPort $ChromaPort -ErrorAction SilentlyContinue)
if ($portCheck.Count -gt 0) {
    Write-Host "Chroma port $ChromaPort is listening"
} else {
    Write-Host "Warning: Chroma port $ChromaPort is not listening yet"
}

Write-Host ""
Write-Host "Reset done. Chroma resources are ready for startup." -ForegroundColor Green
