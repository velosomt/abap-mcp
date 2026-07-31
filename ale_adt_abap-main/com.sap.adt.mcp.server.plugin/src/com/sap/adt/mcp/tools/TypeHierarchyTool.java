package com.sap.adt.mcp.tools;

import java.net.http.HttpResponse;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sap.adt.mcp.sap.AdtRestClient;

/**
 * Tool: sap_type_hierarchy -- List sub-types (or super-types) of the type at a cursor position.
 * Read-only: it only queries the hierarchy, it never edits anything.
 */
public class TypeHierarchyTool extends AbstractMcpTool {

    public static final String NAME = "sap_type_hierarchy";

    public TypeHierarchyTool(AdtRestClient client) {
        super(client);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return "Returns the type hierarchy for the class/interface at the cursor: sub-types by default, or "
            + "super-types when 'superTypes' is true (POST /sap/bc/adt/abapsource/typehierarchy). Strictly read-only: "
            + "it never edits, locks or saves. Provide objectType+objectName, the 1-based 'line' and 'offset', and "
            + "optionally 'source' (if omitted, the saved source is used).";
    }

    @Override
    public JsonObject getInputSchema() {
        JsonObject lineProp = new JsonObject();
        lineProp.addProperty("type", "integer");
        lineProp.addProperty("description", "1-based line number of the cursor.");

        JsonObject offsetProp = new JsonObject();
        offsetProp.addProperty("type", "integer");
        offsetProp.addProperty("description", "0-based column/offset of the cursor on that line.");

        JsonObject superProp = new JsonObject();
        superProp.addProperty("type", "boolean");
        superProp.addProperty("description", "If true, return super-types instead of sub-types. Default false.");

        JsonObject sourceProp = new JsonObject();
        sourceProp.addProperty("type", "string");
        sourceProp.addProperty("description", "Optional in-progress source text. If omitted, the saved source is used.");

        JsonObject properties = new JsonObject();
        properties.add("objectType", AdtUrlResolver.buildTypeProperty());
        properties.add("objectName", AdtUrlResolver.buildNameProperty());
        properties.add("line", lineProp);
        properties.add("offset", offsetProp);
        properties.add("superTypes", superProp);
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
            throw new IllegalArgumentException("Provide 'line' (>=1) and 'offset' (>=0).");
        }
        boolean superTypes = arguments.has("superTypes")
                && !arguments.get("superTypes").isJsonNull()
                && arguments.get("superTypes").getAsBoolean();
        String source = optString(arguments, "source");
        if (source == null) {
            source = client.get(url, "text/plain").body();
        }

        String uriParam = url + "#start=" + line + "," + offset;
        String path = "/sap/bc/adt/abapsource/typehierarchy?uri=" + urlEncode(uriParam)
                + "&type=" + (superTypes ? "superTypes" : "subTypes");

        HttpResponse<String> response = client.post(path, source, "text/plain", "application/*");

        JsonObject output = new JsonObject();
        output.addProperty("statusCode", response.statusCode());
        output.addProperty("response", response.body());
        return output.toString();
    }
}
