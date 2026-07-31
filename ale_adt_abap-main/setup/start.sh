#!/usr/bin/env bash
# ALÊ ADT ABAP — Setup + Start (Linux / macOS)
# Uso: bash setup/start.sh
#
# O script faz tudo em uma tacada:
#   1. Verifica Java 17+
#   2. Lê credenciais de setup/.env
#   3. Baixa o jar do GitHub Releases se não existir localmente
#   4. Adiciona [mcp_servers.ale-adt] em ~/.codex/config.toml (Codex CLI)
#   5. Gera .vscode/mcp.json no diretório atual (VS Code)
#   6. Sobe o servidor MCP (bloqueia até Ctrl+C)

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(dirname "$SCRIPT_DIR")"
ENV_FILE="$SCRIPT_DIR/.env"
JAR_NAME="ale-adt-mcp-standalone.jar"
JAR_LOCAL_PATTERN="$ROOT_DIR/com.sap.adt.mcp.server.standalone/target/ale-adt-mcp-standalone-*.jar"
JAR_CACHE="$SCRIPT_DIR/$JAR_NAME"
GH_RELEASE="https://github.com/333xandelz/ale_adt_abap/releases/latest/download/$JAR_NAME"

# ---------------------------------------------------------------------------
# 1. Java 17+
# ---------------------------------------------------------------------------
if ! command -v java &>/dev/null; then
    echo "ERRO: Java não encontrado. Instale Java 17+: https://adoptium.net" >&2
    exit 1
fi
echo "[1/5] Java: $(java -version 2>&1 | head -1)"

# ---------------------------------------------------------------------------
# 2. Ler .env
# ---------------------------------------------------------------------------
if [ ! -f "$ENV_FILE" ]; then
    echo "ERRO: Arquivo setup/.env não encontrado." >&2
    echo "Copie setup/.env.example para setup/.env e preencha as credenciais." >&2
    exit 1
fi

set -a
# shellcheck source=/dev/null
source "$ENV_FILE"
set +a

for VAR in SAP_URL SAP_USER SAP_PASS; do
    if [ -z "${!VAR:-}" ]; then
        echo "ERRO: $VAR não definido em setup/.env" >&2
        exit 1
    fi
done

MCP_PORT="${MCP_PORT:-3000}"
echo "[2/5] Credenciais carregadas (SAP_URL=$SAP_URL, port=$MCP_PORT)"

# ---------------------------------------------------------------------------
# 3. Localizar ou baixar jar
# ---------------------------------------------------------------------------
JAR=""
# shellcheck disable=SC2086
LOCAL_MATCH=$(ls $JAR_LOCAL_PATTERN 2>/dev/null | head -1 || true)

if [ -n "$LOCAL_MATCH" ]; then
    JAR="$LOCAL_MATCH"
    echo "[3/5] Jar local encontrado: $JAR"
elif [ -f "$JAR_CACHE" ]; then
    JAR="$JAR_CACHE"
    echo "[3/5] Jar em cache: $JAR"
else
    echo "[3/5] Baixando $JAR_NAME do GitHub Releases..."
    if command -v curl &>/dev/null; then
        curl -fL "$GH_RELEASE" -o "$JAR_CACHE"
    elif command -v wget &>/dev/null; then
        wget -q "$GH_RELEASE" -O "$JAR_CACHE"
    else
        echo "ERRO: curl ou wget necessário para baixar o jar." >&2
        exit 1
    fi
    JAR="$JAR_CACHE"
    echo "[3/5] Download concluído: $JAR"
fi

# ---------------------------------------------------------------------------
# 4. Codex CLI — ~/.codex/config.toml
# ---------------------------------------------------------------------------
CODEX_CONFIG="$HOME/.codex/config.toml"
mkdir -p "$(dirname "$CODEX_CONFIG")"

ENTRY="
[mcp_servers.ale-adt]
url = \"http://localhost:${MCP_PORT}/mcp\"
default_tools_approval_mode = \"auto\""

if [ -f "$CODEX_CONFIG" ]; then
    if ! grep -q "\[mcp_servers\.ale-adt\]" "$CODEX_CONFIG"; then
        printf '%s\n' "$ENTRY" >> "$CODEX_CONFIG"
        echo "[4/5] Codex: ale-adt adicionado em $CODEX_CONFIG"
    else
        echo "[4/5] Codex: ale-adt já configurado em $CODEX_CONFIG"
    fi
else
    printf '%s\n' "${ENTRY#$'\n'}" > "$CODEX_CONFIG"
    echo "[4/5] Codex: config.toml criado em $CODEX_CONFIG"
fi

# ---------------------------------------------------------------------------
# 5. VS Code — .vscode/mcp.json no diretório atual
# ---------------------------------------------------------------------------
VSCODE_MCP="$(pwd)/.vscode/mcp.json"
mkdir -p "$(dirname "$VSCODE_MCP")"

cat > "$VSCODE_MCP" <<EOF
{
  "servers": {
    "ale-adt": {
      "type": "http",
      "url": "http://localhost:${MCP_PORT}/mcp"
    }
  }
}
EOF
echo "[5/5] VS Code: .vscode/mcp.json gerado em $VSCODE_MCP"

# ---------------------------------------------------------------------------
# 6. Subir servidor
# ---------------------------------------------------------------------------
echo ""
echo "========================================================"
echo " ALÊ ADT ABAP MCP Server"
echo " URL: http://localhost:${MCP_PORT}/mcp"
echo " Ctrl+C para parar"
echo "========================================================"
echo ""

export SAP_URL SAP_USER SAP_PASS \
       SAP_CLIENT="${SAP_CLIENT:-}" \
       SAP_LANG="${SAP_LANG:-EN}" \
       SAP_INSECURE_SSL="${SAP_INSECURE_SSL:-false}" \
       MCP_PORT

exec java -jar "$JAR"
