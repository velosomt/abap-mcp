package com.sap.adt.mcp.tools;

import java.net.http.HttpResponse;

import com.google.gson.JsonObject;
import com.sap.adt.mcp.sap.AdtRestClient;
import com.sap.adt.mcp.sap.AdtXmlParser;

/**
 * Tool: sap_get_short_dumps -- List recent ABAP runtime errors (short dumps, ST22) for
 * troubleshooting, without needing SAP GUI access to transaction ST22.
 *
 * Ported from the abap-adt-api reference client (src/api/feeds.ts, dumps()): GET
 * /sap/bc/adt/runtime/dumps as an Atom feed, with an optional "$query" filter passed through
 * verbatim (its exact filter syntax wasn't independently confirmed, so this tool does not
 * attempt to validate or rewrite it -- pass whatever your backend's feed reader accepts,
 * or omit it to list everything the backend returns by default).
 */
public class GetShortDumpsTool extends AbstractMcpTool {

    public static final String NAME = "sap_get_short_dumps";

    public GetShortDumpsTool(AdtRestClient client) {
        super(client);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return "List recent ABAP runtime errors (short dumps, ST22) for troubleshooting.";
    }

    @Override
    public JsonObject getInputSchema() {
        JsonObject queryProp = new JsonObject();
        queryProp.addProperty("type", "string");
        queryProp.addProperty("description", "Optional raw filter query passed through to the backend's "
                + "$query parameter (filter syntax is backend-specific). Omit to list the default range.");

        JsonObject properties = new JsonObject();
        properties.add("query", queryProp);

        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.add("properties", properties);

        return schema;
    }

    @Override
    public String execute(JsonObject arguments) throws Exception {
        String query = optString(arguments, "query");
        String path = "/sap/bc/adt/runtime/dumps";
        if (query != null && !query.isEmpty()) {
            path = path + "?$query=" + urlEncode(query);
        }

        HttpResponse<String> response = client.get(path, "application/atom+xml;type=feed");

        JsonObject output = new JsonObject();
        output.add("dumps", AdtXmlParser.parseAtomFeed(response.body()));
        return output.toString();
    }
}
