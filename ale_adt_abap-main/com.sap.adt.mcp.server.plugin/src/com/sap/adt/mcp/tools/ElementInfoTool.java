package com.sap.adt.mcp.tools;

import java.net.http.HttpResponse;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sap.adt.mcp.sap.AdtRestClient;

/**
 * Tool: sap_element_info -- Get type/signature info for the element under a cursor position.
 * Read-only: it only queries element metadata, it never edits or saves the object.
 */
public class ElementInfoTool extends AbstractMcpTool {

    public static final String NAME = "sap_element_info";

    public ElementInfoTool(AdtRestClient client) {
        super(client);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return "Returns metadata (type, declaration, signature, documentation) for the ABAP element at a cursor "
            + "position (POST /sap/bc/adt/abapsource/codecompletion/elementinfo). Strictly read-only: it only "
            + "inspects, it never edits, locks or saves. Provide objectType+objectName, the 1-based 'line' and "
            + "'offset', and optionally 'source' (if omitted, the object's saved source is used).";
    }

    @Override
    public JsonObject getInputSchema() {
        JsonObject lineProp = new JsonObject();
        lineProp.addProperty("type", "integer");
        lineProp.addProperty("description", "1-based line number of the cursor.");

        JsonObject offsetProp = new JsonObject();
        offsetProp.addProperty("type", "integer");
        offsetProp.addProperty("description", "0-based column/offset of the cursor on that line.");

        JsonObject sourceProp = new JsonObject();
        sourceProp.addProperty("type", "string");
        sourceProp.addProperty("description",
            "Optional in-progress source text the line/offset refer to. If omitted, the object's saved source is used.");

        JsonObject properties = new JsonObject();
        properties.add("objectType", AdtUrlResolver.buildTypeProperty());
        properties.add("objectName", AdtUrlResolver.buildNameProperty());
        properties.add("line", lineProp);
        properties.add("offset", offsetProp);
        properties.add("source", sourceProp);

        JsonArray required = new JsonArray();
        required.add("line");
        required.add("offset");

        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.add("properties", properties);
        schema.add("required", required);
        return schema;
    }

    @Override
    public String execute(JsonObject arguments) throws Exception {
        String url = resolveSourceUrlArg(arguments, "url");
        if (url == null) {
            throw new IllegalArgumentException("Provide objectType + objectName (or url).");
        }
        int line = optInt(arguments, "line", -1);
        int offset = optInt(arguments, "offset", -1);
        if (line < 0 || offset < 0) {
            throw new IllegalArgumentException("Provide 'line' (>=1) and 'offset' (>=0) of the cursor.");
        }
        String source = optString(arguments, "source");
        if (source == null) {
            source = client.get(url, "text/plain").body();
        }

        String uriParam = url + "#start=" + line + "," + offset;
        String path = "/sap/bc/adt/abapsource/codecompletion/elementinfo?uri=" + urlEncode(uriParam);

        HttpResponse<String> response = client.post(path, source, "text/plain", "application/*");

        JsonObject output = new JsonObject();
        output.addProperty("statusCode", response.statusCode());
        output.addProperty("response", response.body());
        return output.toString();
    }
}
