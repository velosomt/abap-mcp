package com.sap.adt.mcp.standalone;

import java.util.List;

import com.sap.adt.mcp.sap.AdtRestClient;
import com.sap.adt.mcp.server.McpServer;
import com.sap.adt.mcp.tools.McpTool;
import com.sap.adt.mcp.tools.ToolRegistry;

/**
 * Entrypoint standalone do servidor MCP — roda FORA do Eclipse (para VS Code ou
 * qualquer cliente MCP via HTTP). Sobe o mesmo {@link McpServer} e registra a mesma
 * lista de tools ({@link ToolRegistry}) usada pelo plugin; a única diferença é a
 * origem da configuração: aqui vem de variáveis de ambiente (ou -Dsystem.property),
 * em vez da UI do Eclipse.
 *
 * <p>Variáveis:</p>
 * <ul>
 *   <li><b>SAP_URL</b> (obrigatória) — ex.: https://host:44300</li>
 *   <li><b>SAP_USER</b> (obrigatória)</li>
 *   <li><b>SAP_PASS</b> (obrigatória)</li>
 *   <li><b>SAP_CLIENT</b> (opcional) — mandante, ex.: 100</li>
 *   <li><b>SAP_LANG</b> (opcional, default EN) — ex.: PT</li>
 *   <li><b>SAP_INSECURE_SSL</b> (opcional, default false) — true confia em qualquer certificado</li>
 *   <li><b>MCP_PORT</b> (opcional, default 3000) — porta HTTP do MCP</li>
 * </ul>
 *
 * <p>O cliente MCP conecta em {@code http://localhost:<MCP_PORT>/mcp} (transport HTTP).</p>
 */
public final class StandaloneMain {

    private StandaloneMain() {
    }

    public static void main(String[] args) throws Exception {
        String baseUrl = require("SAP_URL");
        String user = require("SAP_USER");
        String pass = require("SAP_PASS");
        String sapClient = env("SAP_CLIENT", "");
        String language = env("SAP_LANG", "EN");
        boolean insecureSsl = Boolean.parseBoolean(env("SAP_INSECURE_SSL", "false"));
        int port = parsePort(env("MCP_PORT", "3000"));

        System.err.println("[ale-adt-mcp] alvo SAP: " + baseUrl
                + " (client=" + (sapClient.isEmpty() ? "<default>" : sapClient) + ", lang=" + language + ")");

        AdtRestClient client = new AdtRestClient(baseUrl, user, pass, sapClient, language, insecureSsl);
        try {
            client.login();
            System.err.println("[ale-adt-mcp] login SAP OK");
        } catch (Exception e) {
            System.err.println("[ale-adt-mcp] AVISO: login SAP falhou (" + e.getMessage()
                    + "). O servidor sobe assim mesmo; as tools vão falhar até o SAP ficar acessível.");
        }

        List<McpTool> tools = ToolRegistry.createAll(client);
        McpServer server = new McpServer(port);
        server.registerTools(tools);
        server.start();

        System.err.println("[ale-adt-mcp] MCP HTTP pronto em http://localhost:" + port + "/mcp ("
                + tools.size() + " tools). Ctrl+C para parar.");

        // Mantém o processo vivo (o McpServer roda em threads próprias do HttpServer).
        Thread.currentThread().join();
    }

    private static String env(String key, String def) {
        String v = System.getenv(key);
        if (v == null || v.isEmpty()) {
            v = System.getProperty(key);
        }
        return (v == null || v.isEmpty()) ? def : v;
    }

    private static String require(String key) {
        String v = env(key, null);
        if (v == null || v.isEmpty()) {
            System.err.println("ERRO: variável obrigatória ausente: " + key
                    + ". Defina pelo menos SAP_URL, SAP_USER e SAP_PASS "
                    + "(opcionais: SAP_CLIENT, SAP_LANG, SAP_INSECURE_SSL, MCP_PORT).");
            System.exit(2);
        }
        return v;
    }

    private static int parsePort(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            System.err.println("MCP_PORT inválido ('" + value + "'), usando 3000.");
            return 3000;
        }
    }
}
