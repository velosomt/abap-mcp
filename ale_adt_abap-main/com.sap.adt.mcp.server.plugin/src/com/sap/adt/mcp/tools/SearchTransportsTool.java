package com.sap.adt.mcp.tools;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sap.adt.mcp.sap.AdtRestClient;

/**
 * Tool: sap_search_transports -- Busca transportes e tasks (tabelas E070/E071) por
 * owner, data, status, função e/ou objeto contido. Complementa sap_get_transport_requests
 * (que lista só os transportes DO usuário logado) e sap_transport_details (que detalha UM
 * transporte): aqui você pesquisa o organizador inteiro por critérios — "quais requests o
 * fulano abriu em tal período", "qual transporte mexeu no objeto X", etc.
 *
 * <p>Estritamente read-only: monta um SELECT em E070 (opcionalmente com JOIN em E071) e
 * delega ao endpoint de Data Preview usado por sap_sql_query. Não libera, cria nem altera
 * nenhum transporte.</p>
 */
public class SearchTransportsTool extends AbstractMcpTool {

    public static final String NAME = "sap_search_transports";

    public SearchTransportsTool(AdtRestClient client) {
        super(client);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return "Busca transportes/tasks no organizador (E070/E071) por owner, data, status, função e/ou objeto "
                + "contido. Diferente de sap_get_transport_requests (só os do usuário logado) e sap_transport_details "
                + "(um TR específico). Ex.: requests de um autor num período, ou qual TR contém o objeto X. "
                + "Read-only (SELECT via Data Preview).";
    }

    @Override
    public JsonObject getInputSchema() {
        JsonObject properties = new JsonObject();

        JsonObject ownerProp = new JsonObject();
        ownerProp.addProperty("type", "string");
        ownerProp.addProperty("description", "Owner do transporte/task (E070-AS4USER), ex.: 'ALEXANDRE'.");
        properties.add("owner", ownerProp);

        JsonObject fromProp = new JsonObject();
        fromProp.addProperty("type", "string");
        fromProp.addProperty("description", "Data inicial (E070-AS4DATE) no formato AAAAMMDD, ex.: '20260601'.");
        properties.add("fromDate", fromProp);

        JsonObject toProp = new JsonObject();
        toProp.addProperty("type", "string");
        toProp.addProperty("description", "Data final (E070-AS4DATE) no formato AAAAMMDD, ex.: '20260623'.");
        properties.add("toDate", toProp);

        JsonObject statusProp = new JsonObject();
        statusProp.addProperty("type", "string");
        statusProp.addProperty("description",
                "Status (E070-TRSTATUS): D=modificável, L=bloqueado, O=liberação em curso, R=liberado.");
        properties.add("status", statusProp);

        JsonObject funcProp = new JsonObject();
        funcProp.addProperty("type", "string");
        funcProp.addProperty("description",
                "Função (E070-TRFUNCTION): K=workbench request, W=customizing request, S/R/T=tasks, etc.");
        properties.add("function", funcProp);

        JsonObject onlyReqProp = new JsonObject();
        onlyReqProp.addProperty("type", "boolean");
        onlyReqProp.addProperty("description",
                "Se true, retorna só requests principais (E070-STRKORR vazio), excluindo as tasks. Default false.");
        properties.add("onlyRequests", onlyReqProp);

        JsonObject containsProp = new JsonObject();
        containsProp.addProperty("type", "string");
        containsProp.addProperty("description",
                "Nome de objeto que o transporte deve conter (E071-OBJ_NAME). Aciona JOIN com E071. Ex.: 'ZCL_X'.");
        properties.add("containsObject", containsProp);

        JsonObject maxProp = new JsonObject();
        maxProp.addProperty("type", "integer");
        maxProp.addProperty("description", "Máximo de linhas (default 200).");
        properties.add("max", maxProp);

        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.add("properties", properties);
        return schema;
    }

    @Override
    public String execute(JsonObject arguments) throws Exception {
        String owner = optString(arguments, "owner");
        String fromDate = optString(arguments, "fromDate");
        String toDate = optString(arguments, "toDate");
        String status = optString(arguments, "status");
        String function = optString(arguments, "function");
        boolean onlyRequests = arguments.has("onlyRequests")
                && !arguments.get("onlyRequests").isJsonNull()
                && arguments.get("onlyRequests").getAsBoolean();
        String containsObject = optString(arguments, "containsObject");
        int max = optInt(arguments, "max", 200);

        boolean hasFilter = isSet(owner) || isSet(fromDate) || isSet(toDate) || isSet(status)
                || isSet(function) || onlyRequests || isSet(containsObject);
        if (!hasFilter) {
            throw new IllegalArgumentException(
                    "Informe ao menos um filtro: owner, fromDate, toDate, status, function, onlyRequests ou containsObject.");
        }

        StringBuilder sql = new StringBuilder();
        boolean withJoin = isSet(containsObject);
        String h = withJoin ? "h~" : "";

        if (withJoin) {
            sql.append("SELECT DISTINCT h~trkorr, h~trfunction, h~trstatus, h~as4user, h~as4date, h~strkorr ")
               .append("FROM e070 AS h INNER JOIN e071 AS i ON h~trkorr = i~trkorr ")
               .append("WHERE i~obj_name = '").append(SearchRepositoryTool.sqlLiteral(containsObject)).append("'");
        } else {
            sql.append("SELECT trkorr, trfunction, trstatus, as4user, as4date, strkorr FROM e070 WHERE trkorr <> ''");
        }

        if (isSet(owner)) {
            sql.append(" AND ").append(h).append("as4user = '").append(SearchRepositoryTool.sqlLiteral(owner)).append("'");
        }
        if (isSet(fromDate)) {
            sql.append(" AND ").append(h).append("as4date >= '").append(requireDate(fromDate)).append("'");
        }
        if (isSet(toDate)) {
            sql.append(" AND ").append(h).append("as4date <= '").append(requireDate(toDate)).append("'");
        }
        if (isSet(status)) {
            sql.append(" AND ").append(h).append("trstatus = '").append(SearchRepositoryTool.sqlLiteral(status)).append("'");
        }
        if (isSet(function)) {
            sql.append(" AND ").append(h).append("trfunction = '").append(SearchRepositoryTool.sqlLiteral(function)).append("'");
        }
        if (onlyRequests) {
            sql.append(" AND ").append(h).append("strkorr = ''");
        }

        sql.append(" ORDER BY ").append(h).append("as4date DESCENDING, ").append(h).append("trkorr DESCENDING");

        JsonObject queryArgs = new JsonObject();
        queryArgs.addProperty("query", sql.toString());
        queryArgs.addProperty("maxRows", max);

        String result = new SqlQueryTool(client).execute(queryArgs);

        JsonObject out = new JsonObject();
        out.addProperty("source", withJoin ? "E070+E071" : "E070");
        out.addProperty("sql", sql.toString());
        out.add("result", JsonParser.parseString(result));
        return out.toString();
    }

    // ---------------------------------------------------------------- helpers

    private boolean isSet(String s) {
        return s != null && !s.trim().isEmpty();
    }

    /** Valida AAAAMMDD (8 dígitos). Remove '-' se vier 'AAAA-MM-DD'. */
    private String requireDate(String date) {
        String d = date.trim().replace("-", "");
        if (!d.matches("\\d{8}")) {
            throw new IllegalArgumentException("Data inválida '" + date + "': use o formato AAAAMMDD (ex.: 20260623).");
        }
        return d;
    }
}
