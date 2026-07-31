package com.sap.adt.mcp.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sap.adt.mcp.sap.AdtRestClient;

public class AbapGeneratorsGetSchemaTool extends AbstractMcpTool {
    public AbapGeneratorsGetSchemaTool(AdtRestClient client) { super(client); }
    @Override public String getName() { return "abap_generators-get_schema"; }
    @Override public String getDescription() { return "Get the JSON schema for a specific generator."; }
    @Override public JsonObject getInputSchema() {
        JsonObject prop = new JsonObject();
        JsonObject genId = new JsonObject();
        genId.addProperty("type", "string");
        prop.add("generatorId", genId);
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.add("properties", prop);
        JsonArray req = new JsonArray();
        req.add("generatorId");
        schema.add("required", req);
        return schema;
    }
    @Override public String execute(JsonObject arguments) throws Exception {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        JsonObject props = new JsonObject();
        
        JsonObject entity = new JsonObject();
        entity.addProperty("type", "string");
        entity.addProperty("description", "Entity name");
        props.add("entityName", entity);
        
        JsonObject pkg = new JsonObject();
        pkg.addProperty("type", "string");
        pkg.addProperty("description", "Package name");
        props.add("packageName", pkg);

        schema.add("properties", props);
        return schema.toString();
    }
}
