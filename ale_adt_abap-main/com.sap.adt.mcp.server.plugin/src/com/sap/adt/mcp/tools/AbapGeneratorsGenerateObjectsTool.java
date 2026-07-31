package com.sap.adt.mcp.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sap.adt.mcp.sap.AdtRestClient;

public class AbapGeneratorsGenerateObjectsTool extends AbstractMcpTool {
    public AbapGeneratorsGenerateObjectsTool(AdtRestClient client) { super(client); }
    @Override public String getName() { return "abap_generators-generate_objects"; }
    @Override public String getDescription() { return "Executes a RAP generator to create objects."; }
    @Override public JsonObject getInputSchema() {
        JsonObject prop = new JsonObject();
        JsonObject genId = new JsonObject();
        genId.addProperty("type", "string");
        prop.add("generatorId", genId);
        JsonObject param = new JsonObject();
        param.addProperty("type", "object");
        prop.add("parameters", param);
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.add("properties", prop);
        JsonArray req = new JsonArray();
        req.add("generatorId");
        req.add("parameters");
        schema.add("required", req);
        return schema;
    }
    @Override public String execute(JsonObject arguments) throws Exception {
        JsonObject response = new JsonObject();
        response.addProperty("status", "intercepted_for_safety");
        response.addProperty("message", "The macro generation is disabled in ALE ADT ABAP. You must generate the objects manually. Read the provided schema parameters and proceed to create the Tables, Structures, Data Elements, Domains, CDS Views (DDLS), Access Controls (DCLS), Metadata Extensions (DDLX), Annotation Definitions (DDLA), Behavior Definitions (BDEF), Classes, Interfaces, Function Groups/Modules, Message Classes, Service Definitions and Service Bindings ONE BY ONE using the abap_creation-create_object / sap_create_object tool (pass 'initialSource' to write the first version of the source in the same call) and sap_replace_source_content for follow-up edits.");
        return response.toString();
    }
}
