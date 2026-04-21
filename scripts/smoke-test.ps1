param(
    [string]$ApiBaseUrl = "http://localhost:8081",
    [string]$VllmBaseUrl = "http://localhost:8000",
    [string]$Model = "Qwen/Qwen2.5-14B-Instruct",
    [string]$UserName = "user",
    [string]$UserPassword = "user123",
    [string]$AdminName = "admin",
    [string]$AdminPassword = "admin123",
    [int]$TimeoutSec = 45
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$script:PassCount = 0
$script:FailCount = 0
$script:WarnCount = 0

Add-Type -AssemblyName System.Net.Http
$httpHandler = New-Object System.Net.Http.HttpClientHandler
$httpHandler.UseProxy = $false
$script:HttpClient = [System.Net.Http.HttpClient]::new($httpHandler)
$script:HttpClient.Timeout = [TimeSpan]::FromSeconds([Math]::Max(120, $TimeoutSec + 30))

function Write-Step {
    param([string]$Message)
    Write-Host ""
    Write-Host "==> $Message" -ForegroundColor Cyan
}

function Add-Pass {
    param([string]$Message)
    $script:PassCount++
    Write-Host "[PASS] $Message" -ForegroundColor Green
}

function Add-Fail {
    param([string]$Message)
    $script:FailCount++
    Write-Host "[FAIL] $Message" -ForegroundColor Red
}

function Add-Warn {
    param([string]$Message)
    $script:WarnCount++
    Write-Host "[WARN] $Message" -ForegroundColor Yellow
}

function Assert-True {
    param(
        [bool]$Condition,
        [string]$PassMessage,
        [string]$FailMessage
    )
    if ($Condition) {
        Add-Pass $PassMessage
    } else {
        Add-Fail $FailMessage
    }
}

function Invoke-HttpRequest {
    param(
        [ValidateSet("GET", "POST")]
        [string]$Method,
        [string]$Url,
        [hashtable]$Headers = @{},
        [string]$Body = $null,
        [int]$RequestTimeoutSec = 30
    )

    $request = [System.Net.Http.HttpRequestMessage]::new([System.Net.Http.HttpMethod]::$Method, $Url)

    $headersCopy = @{}
    foreach ($k in $Headers.Keys) {
        $headersCopy[$k] = [string]$Headers[$k]
    }

    if (-not [string]::IsNullOrEmpty($Body)) {
        $contentType = "application/json"
        if ($headersCopy.ContainsKey("Content-Type")) {
            $contentType = $headersCopy["Content-Type"]
            $headersCopy.Remove("Content-Type")
        }
        $request.Content = [System.Net.Http.StringContent]::new($Body, [System.Text.Encoding]::UTF8, $contentType)
    }

    foreach ($key in $headersCopy.Keys) {
        $value = [string]$headersCopy[$key]
        $added = $request.Headers.TryAddWithoutValidation($key, $value)
        if (-not $added -and $null -ne $request.Content) {
            $null = $request.Content.Headers.TryAddWithoutValidation($key, $value)
        }
    }

    $cts = New-Object System.Threading.CancellationTokenSource
    $cts.CancelAfter([TimeSpan]::FromSeconds([Math]::Max(1, $RequestTimeoutSec)))

    try {
        $response = $script:HttpClient.SendAsync($request, $cts.Token).GetAwaiter().GetResult()
        $statusCode = [int]$response.StatusCode
        $respBody = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
        return [PSCustomObject]@{
            StatusCode = $statusCode
            Body = $respBody
        }
    } catch {
        return [PSCustomObject]@{
            StatusCode = 0
            Body = $_.Exception.Message
        }
    } finally {
        $request.Dispose()
        $cts.Dispose()
    }
}

function Try-ParseJson {
    param([string]$Text)
    try {
        return ($Text | ConvertFrom-Json)
    } catch {
        return $null
    }
}

function To-Array {
    param([object]$InputObj)
    if ($null -eq $InputObj) {
        return @()
    }
    return @($InputObj)
}

function Get-TokenFromLoginResponse {
    param([object]$JsonObj)
    if ($null -eq $JsonObj) {
        return ""
    }
    if ($null -eq $JsonObj.PSObject.Properties["token"]) {
        return ""
    }
    return [string]$JsonObj.token
}

function Extract-Intent {
    param([string]$SseBody)
    $m = [regex]::Match($SseBody, "event:intent\s*[\r\n]+data:([A-Z]+)")
    if ($m.Success) {
        return $m.Groups[1].Value
    }
    return ""
}

function Extract-RagContexts {
    param([string]$SseBody)
    $m = [regex]::Match($SseBody, "event:rag\s*[\r\n]+data:contexts=(\d+)")
    if ($m.Success) {
        return [int]$m.Groups[1].Value
    }
    return -1
}

function Build-StringFromCodePoints {
    param([int[]]$CodePoints)
    $chars = @($CodePoints | ForEach-Object { [char]$_ })
    return (-join $chars)
}

Write-Host "MindBridge smoke test starting..." -ForegroundColor Yellow
Write-Host "API:   $ApiBaseUrl"
Write-Host "vLLM:  $VllmBaseUrl"
Write-Host "Model: $Model"

Write-Step "1) Basic health checks"
$health = Invoke-HttpRequest -Method GET -Url "$ApiBaseUrl/api/health" -RequestTimeoutSec 10
Assert-True ($health.StatusCode -eq 200 -and $health.Body -match "ok") "health is OK" "health failed (status=$($health.StatusCode))"

$models = Invoke-HttpRequest -Method GET -Url "$VllmBaseUrl/v1/models" -RequestTimeoutSec 12
Assert-True ($models.StatusCode -eq 200) "vLLM /v1/models reachable" "vLLM /v1/models failed (status=$($models.StatusCode))"
Assert-True ($models.Body -match [regex]::Escape($Model)) "target model found in vLLM list" "target model not found in vLLM list: $Model"

Write-Step "2) Login and token"
$userLoginBody = (@{ username = $UserName; password = $UserPassword } | ConvertTo-Json -Compress)
$adminLoginBody = (@{ username = $AdminName; password = $AdminPassword } | ConvertTo-Json -Compress)

$userLogin = Invoke-HttpRequest -Method POST -Url "$ApiBaseUrl/api/auth/login" -Headers @{ "Content-Type" = "application/json" } -Body $userLoginBody -RequestTimeoutSec 15
$adminLogin = Invoke-HttpRequest -Method POST -Url "$ApiBaseUrl/api/auth/login" -Headers @{ "Content-Type" = "application/json" } -Body $adminLoginBody -RequestTimeoutSec 15

$userToken = Get-TokenFromLoginResponse (Try-ParseJson $userLogin.Body)
$adminToken = Get-TokenFromLoginResponse (Try-ParseJson $adminLogin.Body)

Assert-True ($userLogin.StatusCode -eq 200 -and -not [string]::IsNullOrWhiteSpace($userToken)) "user login OK" "user login failed (status=$($userLogin.StatusCode))"
Assert-True ($adminLogin.StatusCode -eq 200 -and -not [string]::IsNullOrWhiteSpace($adminToken)) "admin login OK" "admin login failed (status=$($adminLogin.StatusCode))"

Write-Step "3) Auth and role checks"
$consultQuery = Build-StringFromCodePoints @(
    0x6211,0x6700,0x8fd1,0x538b,0x529b,0x5f88,0x5927,0xff0c,0x603b,0x662f,
    0x7761,0x4e0d,0x7740,0x600e,0x4e48,0x529e
)
$chatBody = (@{ query = $consultQuery; model = $Model } | ConvertTo-Json -Compress)

$unauthChat = Invoke-HttpRequest -Method POST -Url "$ApiBaseUrl/api/chat/stream" -Headers @{ "Content-Type" = "application/json" } -Body $chatBody -RequestTimeoutSec 15
Assert-True ($unauthChat.StatusCode -eq 401) "chat without token is blocked (401)" "chat without token unexpected status=$($unauthChat.StatusCode)"

$userAdmin = Invoke-HttpRequest -Method GET -Url "$ApiBaseUrl/api/admin/reports" -Headers @{ "Authorization" = "Bearer $userToken" } -RequestTimeoutSec 12
Assert-True ($userAdmin.StatusCode -eq 403) "USER blocked from admin endpoint (403)" "USER admin access unexpected status=$($userAdmin.StatusCode)"

$adminReports = Invoke-HttpRequest -Method GET -Url "$ApiBaseUrl/api/admin/reports" -Headers @{ "Authorization" = "Bearer $adminToken" } -RequestTimeoutSec 12
Assert-True ($adminReports.StatusCode -eq 200) "ADMIN can access admin endpoint" "ADMIN admin access failed status=$($adminReports.StatusCode)"

Write-Step "4) RAG and chat main flow"
$kbReload = Invoke-HttpRequest -Method POST -Url "$ApiBaseUrl/api/kb/reload" -Headers @{ "Authorization" = "Bearer $adminToken" } -RequestTimeoutSec 20
$kbJson = Try-ParseJson $kbReload.Body
$kbOk = ($kbReload.StatusCode -eq 200 -and $null -ne $kbJson -and $kbJson.ok -eq $true)
Assert-True $kbOk "knowledge base reload OK" "knowledge base reload failed status=$($kbReload.StatusCode)"

$consultChat = Invoke-HttpRequest -Method POST -Url "$ApiBaseUrl/api/chat/stream" `
    -Headers @{ "Authorization" = "Bearer $userToken"; "Content-Type" = "application/json"; "Accept" = "text/event-stream" } `
    -Body $chatBody -RequestTimeoutSec $TimeoutSec

$consultIntent = Extract-Intent $consultChat.Body
$consultRagCount = Extract-RagContexts $consultChat.Body

Assert-True ($consultChat.StatusCode -eq 200) "consult chat returned 200" "consult chat failed status=$($consultChat.StatusCode)"
Assert-True ($consultChat.Body -match "event:intent") "SSE has intent event" "SSE missing intent event"
Assert-True ($consultChat.Body -match "event:rag") "SSE has rag event" "SSE missing rag event"
Assert-True ($consultChat.Body -match "event:token") "SSE has token event" "SSE missing token event"
Assert-True ($consultChat.Body -match "event:done") "SSE has done event" "SSE missing done event"

if ($consultRagCount -gt 0) {
    Add-Pass "RAG contexts > 0 (contexts=$consultRagCount)"
} else {
    Add-Warn "RAG contexts not greater than 0 (contexts=$consultRagCount). Check retriever if needed."
}

Write-Host "Observed consult intent: $consultIntent"

Write-Step "5) MCP trigger check for risk flow"
$beforeLogsResp = Invoke-HttpRequest -Method GET -Url "$ApiBaseUrl/api/admin/mcp-logs" -Headers @{ "Authorization" = "Bearer $adminToken" } -RequestTimeoutSec 15
$beforeLogs = @(To-Array (Try-ParseJson $beforeLogsResp.Body))
$beforeMaxId = 0
if ($beforeLogs.Count -gt 0) {
    $beforeMaxId = [int](($beforeLogs | Measure-Object -Property id -Maximum).Maximum)
}

$riskQuery = Build-StringFromCodePoints @(0x6211,0x60f3,0x81ea,0x6740,0xff0c,0x4e0d,0x60f3,0x6d3b,0x4e86)
$riskBody = (@{ query = $riskQuery; model = $Model } | ConvertTo-Json -Compress)
$riskChat = Invoke-HttpRequest -Method POST -Url "$ApiBaseUrl/api/chat/stream" `
    -Headers @{ "Authorization" = "Bearer $userToken"; "Content-Type" = "application/json"; "Accept" = "text/event-stream" } `
    -Body $riskBody -RequestTimeoutSec $TimeoutSec
$riskIntent = Extract-Intent $riskChat.Body

Assert-True ($riskChat.StatusCode -eq 200) "risk chat returned 200" "risk chat failed status=$($riskChat.StatusCode)"
Assert-True ($riskIntent -eq "RISK") "risk intent detected as RISK" "risk intent unexpected: $riskIntent"

Start-Sleep -Seconds 2

$afterLogsResp = Invoke-HttpRequest -Method GET -Url "$ApiBaseUrl/api/admin/mcp-logs" -Headers @{ "Authorization" = "Bearer $adminToken" } -RequestTimeoutSec 15
$afterLogs = @(To-Array (Try-ParseJson $afterLogsResp.Body))
$newLogs = @($afterLogs | Where-Object { [int]$_.id -gt $beforeMaxId })

$hasExcel = (@($newLogs | Where-Object { $_.action -eq "excel_write" -and $_.status -eq "SUCCESS" })).Count -gt 0
$hasEmail = (@($newLogs | Where-Object { $_.action -eq "email_alert" -and $_.status -eq "SUCCESS" })).Count -gt 0

Assert-True $hasExcel "MCP excel_write SUCCESS found in new logs" "MCP excel_write SUCCESS not found in new logs"
Assert-True $hasEmail "MCP email_alert SUCCESS found in new logs" "MCP email_alert SUCCESS not found in new logs"

Write-Host ""
Write-Host "===================="
Write-Host "Smoke Test Summary"
Write-Host "Passed: $script:PassCount"
Write-Host "Failed: $script:FailCount"
Write-Host "Warn:   $script:WarnCount"
Write-Host "===================="

if ($script:FailCount -gt 0) {
    exit 1
}

exit 0
