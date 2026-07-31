package com.sap.adt.mcp.tools;

import java.net.http.HttpResponse;

import com.google.gson.JsonObject;
import com.sap.adt.mcp.sap.AdtRestClient;

/**
 * Tool: sap_discovery -- List the ADT services/collections the backend exposes (read-only).
 */
public class DiscoveryTool extends AbstractMcpTool {

    public static final String NAME = "sap_discovery";

    public DiscoveryTool(AdtRestClient client) {
        super(client);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return "Lists the ADT services, collections and template links this SAP backend actually exposes "
            + "(GET /sap/bc/adt/discovery). Strictly read-only diagnostics, takes no parameters. Use it to confirm "
            + "whether a capability/endpoint (e.g. a RAP or business-services collection) exists on THIS system "
            + "before creating objects, instead of finding out through 404/415 errors.";
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
        HttpResponse<String> response = client.get("/sap/bc/adt/discovery", "application/atomsvc+xml");

        JsonObject output = new JsonObject();
        output.addProperty("statusCode", response.statusCode());
        output.addProperty("response", response.body());
        return output.toString();
    }
}
