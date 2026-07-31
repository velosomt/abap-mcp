package com.sap.adt.mcp.tools;

import java.net.http.HttpResponse;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sap.adt.mcp.sap.AdtRestClient;
import com.sap.adt.mcp.sap.AdtXmlParser;

/**
 * Tool: sap_get_transport_requests -- List existing transport requests (TRKORR)
 * from the user's transport organizer tree, via GET /sap/bc/adt/cts/transportrequests.
 * Unlike sap_transport_check (which discovers what transport a *new* change to an
 * object/package combination should use), this lists transports that already exist
 * so the user can pick one to reuse. Real ADT endpoint, no mocked fallback: an
 * empty list means SAP genuinely returned none, not that the call failed silently.
 *
 * Ported from the AWS SAP ABAP Accelerator MCP (get_transport_requests).
 */
public class GetTransportRequestsTool extends AbstractMcpTool {

    public static final String NAME = "sap_get_transport_requests";

    public GetTransportRequestsTool(AdtRestClient client) {
        super(client);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return "List the user's existing transport requests (TRKORR) from SAP's transport organizer, "
                + "optionally filtered by target system. Use this to pick an existing transport to reuse, "
                + "as opposed to sap_transport_check which discovers what a *new* change needs.";
    }

    @Override
    public JsonObject getInputSchema() {
        JsonObject targetProp = new JsonObject();
        targetProp.addProperty("type", "string");
        targetProp.addProperty("description", "Optional target system to filter by (e.g. 'DEV'). Omit to list all.");

        JsonObject properties = new JsonObject();
        properties.add("target", targetProp);

        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.add("properties", properties);
        schema.add("required", new JsonArray());

        return schema;
    }

    @Override
    public String execute(JsonObject arguments) throws Exception {
        String target = optString(arguments, "target");

        String path = "/sap/bc/adt/cts/transportrequests";
        if (target != null && !target.isEmpty()) {
            path = path + "?targets=" + urlEncode(target);
        }

        HttpResponse<String> response = client.get(
                path, "application/vnd.sap.adt.transportorganizertree.v1+xml");

        JsonArray transports = AdtXmlParser.parseTransportRequestsList(response.body());

        JsonObject output = new JsonObject();
        output.add("transports", transports);
        output.addProperty("count", transports.size());
        return output.toString();
    }
}
