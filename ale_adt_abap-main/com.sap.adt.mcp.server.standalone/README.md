# ALÊ ADT MCP — Standalone (sem Eclipse)

Mesmo servidor MCP do plugin Eclipse, empacotado como **jar executável** para rodar
fora do Eclipse (ex.: no **VS Code**, Claude Code, Cline ou qualquer cliente MCP).
Expõe exatamente as mesmas tools do plugin — a lista é compartilhada via
`com.sap.adt.mcp.tools.ToolRegistry`, então plugin e standalone nunca divergem.

> O plugin Eclipse continua existindo e funcionando normalmente. Este módulo é uma
> **forma alternativa de executar o mesmo core**, não um substituto.

## Build

Requer JDK 17+ e Maven. A partir da raiz do repositório:

```bash
mvn -f com.sap.adt.mcp.server.standalone/pom.xml clean package
```

Gera o fat-jar (com Gson embutido) em:

```
com.sap.adt.mcp.server.standalone/target/ale-adt-mcp-standalone-2.0.0.jar
```

O build reaproveita as fontes do plugin (`../com.sap.adt.mcp.server.plugin/src`),
excluindo apenas a camada Eclipse (`ui/`, `preferences/`, `Activator`).

## Configuração (variáveis de ambiente)

| Variável | Obrigatória | Default | Descrição |
|---|---|---|---|
| `SAP_URL` | sim | — | URL do servidor ADT, ex.: `https://host:44300` |
| `SAP_USER` | sim | — | usuário SAP |
| `SAP_PASS` | sim | — | senha SAP |
| `SAP_CLIENT` | não | (default do sistema) | mandante, ex.: `100` |
| `SAP_LANG` | não | `EN` | idioma de logon, ex.: `PT` |
| `SAP_INSECURE_SSL` | não | `false` | `true` confia em qualquer certificado (dev) |
| `MCP_PORT` | não | `3000` | porta HTTP do MCP |

Também aceita como system property: `-DSAP_URL=...`.

## Rodar

```bash
SAP_URL=https://host:44300 SAP_USER=ALEXANDRE SAP_PASS=*** SAP_CLIENT=100 SAP_LANG=PT \
  java -jar com.sap.adt.mcp.server.standalone/target/ale-adt-mcp-standalone-2.0.0.jar
```

O servidor fica em `http://localhost:3000/mcp` (transport HTTP). Mesmo que o login SAP
falhe, o servidor sobe (as tools só falham na execução até o SAP ficar acessível).

## Conectar no VS Code

Crie `.vscode/mcp.json` no seu workspace:

```json
{
  "servers": {
    "ale-adt": { "type": "http", "url": "http://localhost:3000/mcp" }
  }
}
```

Suba o jar (comando acima) e o VS Code passa a enxergar as tools. As credenciais ficam
no ambiente onde você roda o jar, não no `mcp.json`.

> Alternativa sem rodar o jar à parte: manter o plugin Eclipse aberto e apontar o
> `mcp.json` para a porta dele — a config do cliente é idêntica.
