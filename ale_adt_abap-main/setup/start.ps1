# ALÊ ADT ABAP — Setup + Start (Windows PowerShell)
# Uso: .\setup\start.ps1
#
# O script faz tudo em uma tacada:
#   1. Verifica Java 17+
#   2. Lê credenciais de setup\.env
#   3. Baixa o jar do GitHub Releases se não existir localmente
#   4. Adiciona [mcp_servers.ale-adt] em ~/.codex/config.toml (Codex CLI)
#   5. Gera .vscode/mcp.json no diretório atual (VS Code)
#   6. Sobe o servidor MCP (bloqueia até Ctrl+C)

$ErrorActionPreference = "Stop"

$ScriptDir  = Split-Path -Parent $MyInvocation.MyCommand.Path
$RootDir    = Split-Path -Parent $ScriptDir
$EnvFile    = Join-Path $ScriptDir ".env"
$JarName    = "ale-adt-mcp-standalone.jar"
$JarLocal   = Join-Path $RootDir "com.sap.adt.mcp.server.standalone\target\ale-adt-mcp-standalone-*.jar"
$JarCache   = Join-Path $ScriptDir $JarName
$GhRelease  = "https://github.com/333xandelz/ale_adt_abap/releases/latest/download/$JarName"

# ---------------------------------------------------------------------------
# 1. Java 17+
# ---------------------------------------------------------------------------
if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
    Write-Error "Java nao encontrado. Instale Java 17+: https://adoptium.net"
    exit 1
}
$javaVersion = (java --version 2>&1)[0]
Write-Host "[1/5] Java: $javaVersion"

# ---------------------------------------------------------------------------
# 2. Ler .env
# ---------------------------------------------------------------------------
if (-not (Test-Path $EnvFile)) {
    Write-Error "Arquivo setup\.env nao encontrado.`nCopie setup\.env.example para setup\.env e preencha as credenciais."
    exit 1
}

$cfg = @{}
Get-Content $EnvFile | ForEach-Object {
    if ($_ -match "^\s*([^#=\s]+)\s*=\s*(.*)\s*$") {
        $cfg[$Matches[1]] = $Matches[2]
    }
}

foreach ($key in @("SAP_URL", "SAP_USER", "SAP_PASS")) {
    if (-not $cfg[$key]) {
        Write-Error "$key nao definido em setup\.env"
        exit 1
    }
}

$port = if ($cfg["MCP_PORT"]) { $cfg["MCP_PORT"] } else { "3000" }

Write-Host "[2/5] Credenciais carregadas (SAP_URL=$($cfg['SAP_URL']), port=$port)"

# ---------------------------------------------------------------------------
# 3. Localizar ou baixar jar
# ---------------------------------------------------------------------------
$jarResolved = $null
$localMatch  = Get-Item $JarLocal -ErrorAction SilentlyContinue | Select-Object -First 1
if ($localMatch) {
    $jarResolved = $localMatch.FullName
    Write-Host "[3/5] Jar local encontrado: $jarResolved"
} elseif (Test-Path $JarCache) {
    $jarResolved = $JarCache
    Write-Host "[3/5] Jar em cache: $jarResolved"
} else {
    Write-Host "[3/5] Baixando $JarName do GitHub Releases..."
    Invoke-WebRequest -Uri $GhRelease -OutFile $JarCache -UseBasicParsing
    $jarResolved = $JarCache
    Write-Host "[3/5] Download concluido: $jarResolved"
}

# ---------------------------------------------------------------------------
# 4. Codex CLI — ~/.codex/config.toml
# ---------------------------------------------------------------------------
$codexDir    = Join-Path $HOME ".codex"
$codexConfig = Join-Path $codexDir "config.toml"

if (-not (Test-Path $codexDir)) {
    New-Item -ItemType Directory -Path $codexDir | Out-Null
}

$entry = @"

[mcp_servers.ale-adt]
url = "http://localhost:$port/mcp"
default_tools_approval_mode = "auto"
"@

if (Test-Path $codexConfig) {
    $content = Get-Content $codexConfig -Raw
    if ($content -notmatch "\[mcp_servers\.ale-adt\]") {
        Add-Content $codexConfig $entry
        Write-Host "[4/5] Codex: ale-adt adicionado em $codexConfig"
    } else {
        Write-Host "[4/5] Codex: ale-adt ja configurado em $codexConfig"
    }
} else {
    Set-Content $codexConfig $entry.TrimStart() -Encoding utf8
    Write-Host "[4/5] Codex: config.toml criado em $codexConfig"
}

# ---------------------------------------------------------------------------
# 5. VS Code — .vscode/mcp.json no diretorio atual
# ---------------------------------------------------------------------------
$vscodeDir = Join-Path (Get-Location) ".vscode"
$mcpJson   = Join-Path $vscodeDir "mcp.json"

if (-not (Test-Path $vscodeDir)) {
    New-Item -ItemType Directory -Path $vscodeDir | Out-Null
}

@{
    servers = @{
        "ale-adt" = @{
            type = "http"
            url  = "http://localhost:$port/mcp"
        }
    }
} | ConvertTo-Json -Depth 5 | Set-Content $mcpJson -Encoding utf8

Write-Host "[5/5] VS Code: .vscode/mcp.json gerado em $mcpJson"

# ---------------------------------------------------------------------------
# 6. Subir servidor
# ---------------------------------------------------------------------------
Write-Host ""
Write-Host "========================================================"
Write-Host " ALÊ ADT ABAP MCP Server"
Write-Host " URL: http://localhost:$port/mcp"
Write-Host " Ctrl+C para parar"
Write-Host "========================================================"
Write-Host ""

$env:SAP_URL          = $cfg["SAP_URL"]
$env:SAP_USER         = $cfg["SAP_USER"]
$env:SAP_PASS         = $cfg["SAP_PASS"]
$env:SAP_CLIENT       = if ($cfg["SAP_CLIENT"])       { $cfg["SAP_CLIENT"] }       else { "" }
$env:SAP_LANG         = if ($cfg["SAP_LANG"])         { $cfg["SAP_LANG"] }         else { "EN" }
$env:SAP_INSECURE_SSL = if ($cfg["SAP_INSECURE_SSL"]) { $cfg["SAP_INSECURE_SSL"] } else { "false" }
$env:MCP_PORT         = $port

java -jar $jarResolved
