[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot

function Require-Command {
    param([string]$Name, [string]$Hint)

    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        Write-Host "缺少 $Name。$Hint" -ForegroundColor Red
        exit 1
    }
}

function Require-Path {
    param([string]$Path, [string]$Hint)

    if (-not (Test-Path $Path)) {
        Write-Host "未找到 $Path。$Hint" -ForegroundColor Red
        exit 1
    }
}

function Start-DemoProcess {
    param([string]$Title, [string]$Directory, [string]$Command)

    $encodedCommand = [Convert]::ToBase64String([Text.Encoding]::Unicode.GetBytes(
        "Set-Location -LiteralPath '$Directory'; $Command"
    ))
    Start-Process powershell -ArgumentList '-NoExit', '-EncodedCommand', $encodedCommand | Out-Null
    Write-Host "已在新窗口启动：$Title" -ForegroundColor Green
}

Require-Command docker '请安装并启动 Docker Desktop。'
Require-Command mvn '请安装 Maven，并确认 Java 21 已配置。'
Require-Command python '请安装 Python 3.11 或更高版本。'
Require-Command npm '请安装 Node.js 20 或更高版本。'

$infraPath = Join-Path $projectRoot 'infra/docker-compose.yml'
$javaPath = Join-Path $projectRoot 'tourism-service/pom.xml'
$pythonPath = Join-Path $projectRoot 'agent-service'
$webPath = Join-Path $projectRoot 'web/package.json'

Require-Path $infraPath '请先完成基础设施文件。'
Require-Path $javaPath '请先完成 Java 文旅服务。'
Require-Path $pythonPath '请先完成 Python AI 服务。'
Require-Path $webPath '请先完成 Vue 网页端。'

if (-not (Test-Path (Join-Path $pythonPath '.env'))) {
    Write-Host '提示：未发现 agent-service/.env。可先演示本地数据；配置 DeepSeek、百炼和 LangSmith Key 后可启用对应 AI 能力。' -ForegroundColor Yellow
}

Push-Location $projectRoot
try {
    docker compose -f $infraPath up -d
}
finally {
    Pop-Location
}

Start-DemoProcess 'Java 文旅服务：http://127.0.0.1:8080' (Join-Path $projectRoot 'tourism-service') 'mvn spring-boot:run'
Start-DemoProcess 'Python AI 服务：http://127.0.0.1:8000' $pythonPath 'python -m uvicorn app.main:app --reload --host 127.0.0.1 --port 8000'
Start-DemoProcess 'Vue 门户与后台：http://127.0.0.1:5173' (Join-Path $projectRoot 'web') 'npm run dev -- --host 127.0.0.1 --port 5173'

Write-Host ''
Write-Host '本机演示入口：' -ForegroundColor Cyan
Write-Host '  PC 门户与管理后台：http://127.0.0.1:5173'
Write-Host '  Java 服务：http://127.0.0.1:8080'
Write-Host '  AI 服务：http://127.0.0.1:8000'
Write-Host '  小程序：请在微信开发者工具打开 miniprogram 目录，并关闭本地调试域名校验。'
Write-Host 'API Key 仅放在本机 agent-service/.env，禁止提交到仓库。' -ForegroundColor Yellow
