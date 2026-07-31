package com.sap.adt.mcp.tools;

import java.net.http.HttpResponse;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sap.adt.mcp.sap.AdtRestClient;

/**
 * Tool: sap_find_definition -- Resolve the definition (or implementation) target of a symbol at a cursor.
 * Read-only navigation: it only returns where the symbol is defined, it never edits anything.
 */
public class FindDefinitionTool extends AbstractMcpTool {

    public static final String NAME = "sap_find_definition";

    public FindDefinitionTool(AdtRestClient client) {
        super(client);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return "Resolves where the symbol under the cursor is defined (go-to-definition), or its implementation "
            + "when 'implementation' is true (POST /sap/bc/adt/navigation/target). Strictly read-only navigation: it "
            + "returns the target object URL, it never edits, locks or saves. Provide objectType+objectName, the "
            + "1-based 'line', the 'startColumn' (and optional 'endColumn') spanning the symbol, and optionally "
            + "'source' (if omitted, the saved source is used).";
    }

    @Override
    public JsonObject getInputSchema() {
        JsonObject lineProp = new JsonObject();
        lineProp.addProperty("type", "integer");
        lineProp.addProperty("description", "1-based line number of the symbol.");

        JsonObject startProp = new JsonObject();
        startProp.addProperty("type", "integer");
        startProp.addProperty("description", "0-based start column of the symbol on that line.");

        JsonObject endProp = new JsonObject();
        endProp.addProperty("type", "integer");
        endProp.addProperty("description", "Optional 0-based end column of the symbol (defaults to startColumn).");

        JsonObject implProp = new JsonObject();
        implProp.addProperty("type", "boolean");
        implProp.addProperty("description", "If true, resolve the implementation instead of the definition. Default false.");

        JsonObject sourceProp = new JsonObject();
        sourceProp.addProperty("type", "string");
        sourceProp.addProperty("description", "Optional in-progress source text. If omitted, the saved source is used.");

        JsonObject properties = new JsonObject();
        properties.add("objectType", AdtUrlResolver.buildTypeProperty());
        properties.add("objectName", AdtUrlResolver.buildNameProperty());
        properties.add("line", lineProp);
        properties.add("startColumn", startProp);
        properties.add("endColumn", endProp);
        properties.add("implementation", implProp);
        properties.add("source", sourceProp);

        JsonArray required = new JsonArray();
        required.add("line");
        required.add("startColumn");

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
        int startCol = optInt(arguments, "startColumn", -1);
        if (line < 0 || startCol < 0) {
            throw new IllegalArgumentException("Provide 'line' (>=1) and 'startColumn' (>=0).");
        }
        int endCol = optInt(arguments, "endColumn", startCol);
        boolean implementation = arguments.has("implementation")
                && !arguments.get("implementation").isJsonNull()
                && arguments.get("implementation").getAsBoolean();
        String source = optString(arguments, "source");
        if (source == null) {
            source = client.get(url, "text/plain").body();
        }

        String uriParam = url + "#start=" + line + "," + startCol + ";end=" + line + "," + endCol;
        String path = "/sap/bc/adt/navigation/target?uri=" + urlEncode(uriParam)
                + "&filter=" + (implementation ? "implementation" : "definition");

        HttpResponse<String> response = client.post(path, source, "text/plain", "application/*");

        JsonObject output = new JsonObject();
        output.addProperty("statusCode", response.statusCode());
        output.addProperty("response", response.body());
        return output.toString();
    }
}
