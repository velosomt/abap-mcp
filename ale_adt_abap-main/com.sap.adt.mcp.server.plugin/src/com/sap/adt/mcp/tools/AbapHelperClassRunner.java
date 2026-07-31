package com.sap.adt.mcp.tools;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sap.adt.mcp.sap.AdtRestClient;

/**
 * Roda uma operacao ABAP nativa sem endpoint REST direto no ADT: cria uma classe
 * IF_OO_ADT_CLASSRUN temporaria com o source informado, ativa, executa via classrun
 * e por padrao remove a classe ao final. Mesma tecnica usada pelo MCP de referencia
 * joamel01/SAP_MCP para criar transaction code e ler/gravar parametro de usuario --
 * operacoes que o ADT REST nao expoe diretamente, mas que sao triviais a partir de
 * ABAP nativo (CALL FUNCTION).
 */
final class AbapHelperClassRunner {

    private AbapHelperClassRunner() {
    }

    /**
     * @return o JSON de retorno de sap_execute_console (statusCode/status/output) para a
     *         classe helper já executada.
     */
    static String run(AdtRestClient client, String helperClassName, String packageName,
            String abapSource, String transport, boolean deleteAfterRun) throws Exception {

        JsonObject createArgs = new JsonObject();
        createArgs.addProperty("objtype", "CLAS/OC");
        createArgs.addProperty("name", helperClassName);
        createArgs.addProperty("parentName", packageName);
        createArgs.addProperty("description", "MCP helper class (temporary)");
        if (transport != null && !transport.isEmpty()) {
            createArgs.addProperty("transport", transport);
        }
        String createResultJson = new CreateObjectTool(client).execute(createArgs);
        JsonObject createResult = JsonParser.parseString(createResultJson).getAsJsonObject();
        if (createResult.has("status") && "error".equals(createResult.get("status").getAsString())) {
            String errorBody = createResult.has("errorBody") ? createResult.get("errorBody").getAsString() : "";
            if (!errorBody.toLowerCase().contains("exist")) {
                throw new IllegalStateException(
                        "Failed to create helper class " + helperClassName + ": " + errorBody);
            }
            // Classe helper já existe (de uma chamada anterior não limpa) -- segue e
            // sobrescreve o source dela abaixo.
        }

        JsonObject setSourceArgs = new JsonObject();
        setSourceArgs.addProperty("objectType", "CLAS/OC");
        setSourceArgs.addProperty("objectName", helperClassName);
        setSourceArgs.addProperty("source", abapSource);
        if (transport != null && !transport.isEmpty()) {
            setSourceArgs.addProperty("transport", transport);
        }
        new SetSourceTool(client).execute(setSourceArgs);

        JsonObject runArgs = new JsonObject();
        runArgs.addProperty("className", helperClassName);
        String runResult = new ExecuteConsoleTool(client).execute(runArgs);

        if (deleteAfterRun) {
            try {
                JsonObject deleteArgs = new JsonObject();
                deleteArgs.addProperty("objectType", "CLAS/OC");
                deleteArgs.addProperty("objectName", helperClassName);
                if (transport != null && !transport.isEmpty()) {
                    deleteArgs.addProperty("transport", transport);
                }
                new DeleteObjectTool(client).execute(deleteArgs);
            } catch (Exception ignored) {
                // Limpeza best-effort: não falha a operação principal por não conseguir
                // remover a classe helper temporária.
            }
        }

        return runResult;
    }

    /** Literal de string ABAP, com aspas simples internas duplicadas (escaping nativo ABAP). */
    static String toAbapLiteral(String value) {
        String safe = value == null ? "" : value;
        return "'" + safe.replace("'", "''") + "'";
    }

    /** Trunca e normaliza um nome de objeto ABAP (maiúsculas, limite de tamanho). */
    static String truncateName(String name, int maxLen) {
        if (name == null) return null;
        String upper = name.toUpperCase();
        return upper.length() > maxLen ? upper.substring(0, maxLen) : upper;
    }

    /**
     * Extrai pares CHAVE=VALOR de uma linha de saída no formato usado pelas classes
     * helper geradas aqui (ex: "MODE=CREATE; TCODE=Z001; RESULT=OK; SUBRC=0; EXISTS=1"
     * ou "PARAM;PARID=XXX;PARVA=YYY;PARTEXT=ZZZ"). Heurística simples por split em
     * ";" -- suficiente para os campos estruturados; o texto bruto completo deve
     * sempre ser incluído à parte na resposta para o caso de mensagens de exceção
     * livres que não respeitem este formato.
     */
    static JsonObject parseSemicolonFields(String line) {
        JsonObject obj = new JsonObject();
        if (line == null || line.isEmpty()) return obj;
        for (String part : line.split(";\\s*")) {
            int eq = part.indexOf('=');
            if (eq > 0) {
                obj.addProperty(part.substring(0, eq).trim(), part.substring(eq + 1).trim());
            }
        }
        return obj;
    }

    /** Monta um literal ABAP "#( ( 'A' ) ( 'B' ) )" a partir de uma lista de IDs (tabela de filtro). */
    static String buildIdTableLiteral(java.util.List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return "#( )";
        }
        StringBuilder sb = new StringBuilder("#( ");
        for (String id : ids) {
            sb.append("( ").append(toAbapLiteral(id.toUpperCase())).append(" ) ");
        }
        return sb.toString().trim() + " )";
    }

    /** Extrai o campo "output" (texto bruto do console) do JSON devolvido por sap_execute_console. */
    static String extractConsoleOutput(String executeConsoleResultJson) {
        JsonObject result = JsonParser.parseString(executeConsoleResultJson).getAsJsonObject();
        if (result.has("status") && "error".equals(result.get("status").getAsString())) {
            String errorBody = result.has("errorBody") ? result.get("errorBody").getAsString() : "";
            throw new IllegalStateException("Helper class execution failed: " + errorBody);
        }
        return result.has("output") ? result.get("output").getAsString() : "";
    }
}
