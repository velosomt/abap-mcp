package com.sap.adt.mcp.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sap.adt.mcp.sap.AdtRestClient;

public class AbapGeneratorsListTool extends AbstractMcpTool {
    public AbapGeneratorsListTool(AdtRestClient client) { super(client); }
    @Override public String getName() { return "abap_generators-list_generators"; }
    @Override public String getDescription() { return "List available ABAP RAP generators."; }
    @Override public JsonObject getInputSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.add("properties", new JsonObject());
        return schema;
    }
    @Override public String execute(JsonObject arguments) throws Exception {
        JsonObject gen = new JsonObject();
        gen.addProperty("id", "odata_ui_service_from_scratch");
        gen.addProperty("name", "OData UI Service from Scratch");
        JsonArray arr = new JsonArray();
        arr.add(gen);
        JsonObject result = new JsonObject();
        result.add("generators", arr);
        return result.toString();
    }
}
