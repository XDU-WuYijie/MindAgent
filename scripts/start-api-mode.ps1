param(
    [Parameter(Mandatory = $true)]
    [string]$ApiKey,

    [Parameter(Mandatory = $false)]
    [string]$BaseUrl = "https://api.openai.com",

    [Parameter(Mandatory = $false)]
    [string]$Model = "gpt-4o-mini",

    [Parameter(Mandatory = $false)]
    [int]$ServerPort = 8081
)

$ErrorActionPreference = "Stop"

Write-Host "==> Starting MindBridge in API mode" -ForegroundColor Cyan
Write-Host "BaseUrl: $BaseUrl"
Write-Host "Model:   $Model"
Write-Host "Port:    $ServerPort"

$env:SPRING_PROFILES_ACTIVE = "api"
$env:SPRING_AI_OPENAI_BASE_URL = $BaseUrl
$env:SPRING_AI_OPENAI_API_KEY = $ApiKey
$env:SPRING_AI_OPENAI_MODEL = $Model
$env:SERVER_PORT = "$ServerPort"

Push-Location "$PSScriptRoot\..\backend"
try {
    mvn spring-boot:run
}
finally {
    Pop-Location
}
