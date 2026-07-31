package com.sap.adt.mcp.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sap.adt.mcp.sap.AdtRestClient;

/**
 * Tool: sap_get_table_schema -- Retrieve DDIC table schema (fields, types)
 */
public class GetTableSchemaTool extends AbstractMcpTool {

    public static final String NAME = "sap_get_table_schema";

    public GetTableSchemaTool(AdtRestClient client) {
        super(client);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return "Obtém o schema (estrutura de campos, tipos de dados, tamanho) de uma tabela ou estrutura do SAP (DDIC). Fundamental para saber quais colunas existem antes de codificar.";
    }

    @Override
    public JsonObject getInputSchema() {
        JsonObject tableNameProp = new JsonObject();
        tableNameProp.addProperty("type", "string");
        tableNameProp.addProperty("description", "Nome da tabela ou estrutura (ex: MARA, VBAK, BSEG, ZTABELA)");

        JsonObject properties = new JsonObject();
        properties.add("tableName", tableNameProp);

        JsonArray required = new JsonArray();
        required.add("tableName");

        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.add("properties", properties);
        schema.add("required", required);

        return schema;
    }

    @Override
    public String execute(JsonObject arguments) throws Exception {
        String tableName = arguments.get("tableName").getAsString().toUpperCase().trim();

        // Vamos usar a SqlQueryTool internamente para buscar o schema na tabela DD03L
        SqlQueryTool sqlTool = new SqlQueryTool(client);
        
        String query = "SELECT position, fieldname, keyflag, rollname, datatype, leng, decimals " +
                       "FROM dd03l WHERE tabname = '" + tableName + "' " +
                       "AND as4local = 'A' ORDER BY position";

        JsonObject sqlArgs = new JsonObject();
        sqlArgs.addProperty("query", query);
        sqlArgs.addProperty("maxRows", 500);

        String result = sqlTool.execute(sqlArgs);
        
        JsonObject output = new JsonObject();
        output.addProperty("table", tableName);
        output.addProperty("description", "Estrutura da Tabela (DDIC)");
        
        try {
            JsonElement parsedResult = JsonParser.parseString(result);
            output.add("fields", parsedResult);
        } catch (Exception e) {
            output.addProperty("raw_result", result);
        }

        return output.toString();
    }
}
