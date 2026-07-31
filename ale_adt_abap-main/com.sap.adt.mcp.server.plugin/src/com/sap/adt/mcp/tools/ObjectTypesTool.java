package com.sap.adt.mcp.tools;

import java.net.http.HttpResponse;

import com.google.gson.JsonObject;
import com.sap.adt.mcp.sap.AdtRestClient;

/**
 * Tool: sap_object_types -- List the repository object types supported by the backend (read-only).
 */
public class ObjectTypesTool extends AbstractMcpTool {

    public static final String NAME = "sap_object_types";

    public ObjectTypesTool(AdtRestClient client) {
        super(client);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return "Lists the repository object types the backend knows about, with their ADT type keys and descriptions "
            + "(GET /sap/bc/adt/repository/informationsystem/objecttypes). Strictly read-only, takes no parameters. "
            + "Use it to discover valid object-type identifiers supported on this system.";
    }

    @Override
    public JsonObject getInputSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.add("properties", new JsonObject());
        return schema;
    }

    @Override
    public String execute(JsonObject arguments) throws Exception {
        HttpResponse<String> response = client.get(
            "/sap/bc/adt/repository/informationsystem/objecttypes", "application/*");

        JsonObject output = new JsonObject();
        output.addProperty("statusCode", response.statusCode());
        output.addProperty("response", response.body());
        return output.toString();
    }
}
