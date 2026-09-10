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
    param([string]$Title, [string]$Directory, [string]$Command, [string]$LogName)

    $logDirectory = Join-Path $projectRoot '.demo-logs'
    New-Item -ItemType Directory -Path $logDirectory -Force | Out-Null
    $logPath = Join-Path $logDirectory $LogName
    $encodedCommand = [Convert]::ToBase64String([Text.Encoding]::Unicode.GetBytes(
        "Set-Location -LiteralPath '$Directory'; $Command *> '$logPath'"
    ))
    Start-Process powershell -WindowStyle Hidden -ArgumentList '-NoProfile', '-EncodedCommand', $encodedCommand | Out-Null
    Write-Host "已在后台启动：$Title（日志：$logPath）" -ForegroundColor Green
}

function Resolve-MavenExecutable {
    $command = Get-Command mvn -ErrorAction SilentlyContinue
    if ($command) {
        return $command.Source
    }

    $wrapperRoot = Join-Path $env:USERPROFILE '.m2\wrapper\dists'
    $cached = Get-ChildItem -LiteralPath $wrapperRoot -Filter mvn.cmd -Recurse -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1
    if ($cached) {
        return $cached.FullName
    }

    Write-Host '缺少 Maven。请安装 Maven，或先运行一次包含 Maven Wrapper 的项目。' -ForegroundColor Red
    exit 1
}

function Test-TcpPort {
    param([int]$Port)

    $client = [Net.Sockets.TcpClient]::new()
    try {
        $connected = $client.ConnectAsync('127.0.0.1', $Port).Wait(500)
        return $connected -and $client.Connected
    }
    catch {
        return $false
    }
    finally {
        $client.Dispose()
    }
}

function Write-PemFile {
    param([byte[]]$Bytes, [string]$Label, [string]$Path)

    $base64 = [Convert]::ToBase64String($Bytes)
    $lines = for ($offset = 0; $offset -lt $base64.Length; $offset += 64) {
        $length = [Math]::Min(64, $base64.Length - $offset)
        $base64.Substring($offset, $length)
    }
    $content = "-----BEGIN $Label-----`n$($lines -join "`n")`n-----END $Label-----`n"
    [IO.File]::WriteAllText($Path, $content, [Text.Encoding]::ASCII)
}

function New-Base64UrlSecret {
    $bytes = [byte[]]::new(32)
    [Security.Cryptography.RandomNumberGenerator]::Fill($bytes)
    return [Convert]::ToBase64String($bytes).TrimEnd('=').Replace('+', '-').Replace('/', '_')
}

function Ensure-DemoSecrets {
    $secretDirectory = Join-Path $projectRoot '.demo-secrets'
    New-Item -ItemType Directory -Path $secretDirectory -Force | Out-Null

    $privateKey = Join-Path $secretDirectory 'jwt-private.pem'
    $publicKey = Join-Path $secretDirectory 'jwt-public.pem'
    if (-not (Test-Path $privateKey) -or -not (Test-Path $publicKey)) {
        $rsa = [Security.Cryptography.RSA]::Create(2048)
        try {
            Write-PemFile $rsa.ExportPkcs8PrivateKey() 'PRIVATE KEY' $privateKey
            Write-PemFile $rsa.ExportSubjectPublicKeyInfo() 'PUBLIC KEY' $publicKey
        }
        finally {
            $rsa.Dispose()
        }
    }

    $javaToAi = Join-Path $secretDirectory 'java-to-ai.txt'
    $aiToJava = Join-Path $secretDirectory 'ai-to-java.txt'
    $adminPassword = Join-Path $secretDirectory 'admin-password.txt'
    foreach ($secretFile in @($javaToAi, $aiToJava, $adminPassword)) {
        if (-not (Test-Path $secretFile)) {
            [IO.File]::WriteAllText($secretFile, (New-Base64UrlSecret), [Text.Encoding]::ASCII)
        }
    }

    $env:JWT_KEY_ID = 'wudong-demo-rsa'
    $env:JWT_PRIVATE_KEY_FILE = $privateKey
    $env:JWT_PUBLIC_KEY_FILE = $publicKey
    $env:JAVA_TO_AI_CREDENTIAL_FILE = $javaToAi
    $env:AI_TO_JAVA_CREDENTIAL_FILE = $aiToJava
    $env:AI_BASE_URL = 'http://127.0.0.1:8002'
    $env:WUDONG_ADMIN_INIT = 'true'
    $env:WUDONG_ADMIN_USERNAME = 'wudong_admin'
    $env:WUDONG_ADMIN_PASSWORD_FILE = $adminPassword
}

Require-Command docker '请安装并启动 Docker Desktop。'
Require-Command python '请安装 Python 3.11 或更高版本。'
Require-Command npm '请安装 Node.js 20 或更高版本。'
$mavenExecutable = Resolve-MavenExecutable
Ensure-DemoSecrets

$infraPath = Join-Path $projectRoot 'infra/docker-compose.yml'
$javaPath = Join-Path $projectRoot 'tourism-service/pom.xml'
$pythonPath = Join-Path $projectRoot 'agent-service'
$webPath = Join-Path $projectRoot 'web/package.json'

Require-Path $infraPath '请先完成基础设施文件。'
Require-Path $javaPath '请先完成 Java 文旅服务。'
Require-Path $pythonPath '请先完成 Python AI 服务。'
Require-Path $webPath '请先完成 Vue 网页端。'

$mediaScript = Join-Path $projectRoot 'scripts\prepare-local-media.ps1'
$mediaSentinel = Join-Path $projectRoot 'web\public\images\wudong-local\mountain-thumb.jpg'
if (-not (Test-Path -LiteralPath $mediaSentinel) -and (Test-Path -LiteralPath $mediaScript)) {
    try {
        & $mediaScript
    }
    catch {
        Write-Host "提示：本机实景图生成失败，将显示文字占位。$($_.Exception.Message)" -ForegroundColor Yellow
    }
}

$contentMediaScript = Join-Path $projectRoot 'scripts\prepare-content-media.ps1'
$contentMediaRoot = Join-Path $projectRoot 'web\public\images\wudong-content'
$contentMediaComplete = $true
# 当前获批的《乌东衣食住行》包含 228 张原图，每张均需正图和缩略图。
foreach ($imageNumber in 1..228) {
    if (-not [System.IO.File]::Exists((Join-Path $contentMediaRoot "image$imageNumber.jpg")) -or
        -not [System.IO.File]::Exists((Join-Path $contentMediaRoot "image$imageNumber-thumb.jpg"))) {
        $contentMediaComplete = $false
        break
    }
}
if (-not $contentMediaComplete) {
    try { & $contentMediaScript }
    catch { Write-Warning $_.Exception.Message }
}

if (-not (Test-Path (Join-Path $pythonPath '.env'))) {
    Write-Host '提示：未发现 agent-service/.env。目录和下单仍可演示；配置 DeepSeek Key 后再演示 AI。' -ForegroundColor Yellow
}

$venvPython = Join-Path $pythonPath '.venv\Scripts\python.exe'
if (-not (Test-Path $venvPython)) {
    Write-Host '首次启动：正在创建 AI Python 环境并安装依赖……' -ForegroundColor Cyan
    python -m venv (Join-Path $pythonPath '.venv')
    & $venvPython -m pip install --disable-pip-version-check -e $pythonPath
}

if (-not (Test-Path (Join-Path $projectRoot 'web\node_modules'))) {
    Write-Host '首次启动：正在安装 Web 依赖……' -ForegroundColor Cyan
    Push-Location (Join-Path $projectRoot 'web')
    try {
        npm install --no-audit --no-fund
    }
    finally {
        Pop-Location
    }
}

if ((Test-TcpPort 3307) -and (Test-TcpPort 6379)) {
    Write-Host '检测到本机 MySQL(3307) 与 Redis(6379)，直接复用。' -ForegroundColor Green
}
else {
    Push-Location $projectRoot
    try {
        docker compose -f $infraPath up -d
    }
    finally {
        Pop-Location
    }
}

Start-DemoProcess 'Java 文旅服务：http://127.0.0.1:8080' (Join-Path $projectRoot 'tourism-service') "& '$mavenExecutable' spring-boot:run" 'java.log'
Start-DemoProcess 'Python AI 服务：http://127.0.0.1:8002' $pythonPath "& '$venvPython' -m uvicorn app.main:app --host 127.0.0.1 --port 8002" 'ai.log'
Start-DemoProcess 'Vue 门户与后台：http://127.0.0.1:5174' (Join-Path $projectRoot 'web') 'npm run dev -- --host 127.0.0.1 --port 5174' 'web.log'

Write-Host ''
Write-Host '本机演示入口：' -ForegroundColor Cyan
Write-Host '  PC 门户与管理后台：http://127.0.0.1:5174'
Write-Host '  Java 服务：http://127.0.0.1:8080'
Write-Host '  AI 服务：http://127.0.0.1:8002'
Write-Host '  小程序：请在微信开发者工具打开 miniprogram 目录，并关闭本地调试域名校验。'
Write-Host "  后台账号：wudong_admin；密码见 $projectRoot\.demo-secrets\admin-password.txt"
Write-Host 'API Key 仅放在本机 agent-service/.env，禁止提交到仓库。' -ForegroundColor Yellow
