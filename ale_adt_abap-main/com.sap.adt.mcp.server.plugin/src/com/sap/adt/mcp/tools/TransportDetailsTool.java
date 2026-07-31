package com.sap.adt.mcp.tools;

import java.net.http.HttpResponse;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sap.adt.mcp.sap.AdtRestClient;

/**
 * Tool: sap_transport_details -- Read the details (tasks, objects, status) of one transport request.
 * Strictly read-only.
 */
public class TransportDetailsTool extends AbstractMcpTool {

    public static final String NAME = "sap_transport_details";

    public TransportDetailsTool(AdtRestClient client) {
        super(client);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return "Reads the details of a single transport request (its tasks, owner, status and contained objects) "
            + "via GET /sap/bc/adt/cts/transportrequests/{number}. Strictly read-only: it does not create, release "
            + "or modify anything. Provide the request number in 'transport'.";
    }

    @Override
    public JsonObject getInputSchema() {
        JsonObject transportProp = new JsonObject();
        transportProp.addProperty("type", "string");
        transportProp.addProperty("description", "Transport request number, e.g. 'DEVK900123'.");

        JsonObject properties = new JsonObject();
        properties.add("transport", transportProp);

        JsonArray required = new JsonArray();
        required.add("transport");

        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.add("properties", properties);
        schema.add("required", required);
        return schema;
    }

    @Override
    public String execute(JsonObject arguments) throws Exception {
        String transport = optString(arguments, "transport");
        if (transport == null || transport.isEmpty()) {
            throw new IllegalArgumentException("Provide 'transport' (the request number).");
        }
        transport = transport.toUpperCase();

        HttpResponse<String> response = client.get(
            "/sap/bc/adt/cts/transportrequests/" + urlEncode(transport), "application/*");

        JsonObject output = new JsonObject();
        output.addProperty("statusCode", response.statusCode());
        output.addProperty("response", response.body());
        return output.toString();
    }
}
