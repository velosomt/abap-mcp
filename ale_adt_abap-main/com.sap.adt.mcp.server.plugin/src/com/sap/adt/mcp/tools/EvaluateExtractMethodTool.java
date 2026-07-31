package com.sap.adt.mcp.tools;

import java.net.http.HttpResponse;

import com.google.gson.JsonObject;
import com.sap.adt.mcp.sap.AdtRestClient;

/**
 * Tool: sap_evaluate_extract_method -- Impact analysis (read-only, dry-run) for extracting
 * a code range into a new method: what SAP proposes as parameters/exceptions, for manual
 * review. Same reasoning as EvaluateRenameTool for stopping at "evaluate": the
 * preview/execute steps need a generated XML body whose exact schema wasn't independently
 * confirmed, so this port only exposes the safe, body-less evaluate step.
 *
 * Ported from the abap-adt-api reference client (src/api/refactor.ts, extractMethodEvaluate):
 * POST /sap/bc/adt/refactorings?step=evaluate&rel=.../extractmethod.
 */
public class EvaluateExtractMethodTool extends AbstractMcpTool {

    public static final String NAME = "sap_evaluate_extract_method";

    public EvaluateExtractMethodTool(AdtRestClient client) {
        super(client);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return "Read-only impact analysis for extracting a code range into a new method: shows SAP's "
                + "proposed signature (parameters/exceptions), without changing any code. Provide "
                + "objectType+objectName (or objectSourceUrl) and the 1-based start/end line+column range.";
    }

    @Override
    public JsonObject getInputSchema() {
        JsonObject startLineProp = new JsonObject();
        startLineProp.addProperty("type", "integer");
        startLineProp.addProperty("description", "1-based start line of the code range.");
        JsonObject startColProp = new JsonObject();
        startColProp.addProperty("type", "integer");
        startColProp.addProperty("description", "0-based start column.");
        JsonObject endLineProp = new JsonObject();
        endLineProp.addProperty("type", "integer");
        endLineProp.addProperty("description", "1-based end line of the code range.");
        JsonObject endColProp = new JsonObject();
        endColProp.addProperty("type", "integer");
        endColProp.addProperty("description", "0-based end column.");

        JsonObject properties = new JsonObject();
        properties.add("objectType", AdtUrlResolver.buildTypeProperty());
        properties.add("objectName", AdtUrlResolver.buildNameProperty());
        properties.add("startLine", startLineProp);
        properties.add("startColumn", startColProp);
        properties.add("endLine", endLineProp);
        properties.add("endColumn", endColProp);

        com.google.gson.JsonArray required = new com.google.gson.JsonArray();
        required.add("startLine");
        required.add("startColumn");
        required.add("endLine");
        required.add("endColumn");

        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.add("properties", properties);
        schema.add("required", required);

        return schema;
    }

    @Override
    public String execute(JsonObject arguments) throws Exception {
        String sourceUrl = resolveSourceUrlArg(arguments, "objectSourceUrl");
        if (sourceUrl == null) {
            throw new IllegalArgumentException("Provide objectType + objectName (or objectSourceUrl).");
        }
        for (String key : new String[]{"startLine", "startColumn", "endLine", "endColumn"}) {
            if (!arguments.has(key)) {
                throw new IllegalArgumentException("Provide startLine, startColumn, endLine and endColumn.");
            }
        }
        int startLine = arguments.get("startLine").getAsInt();
        int startColumn = arguments.get("startColumn").getAsInt();
        int endLine = arguments.get("endLine").getAsInt();
        int endColumn = arguments.get("endColumn").getAsInt();

        String uriFragment = sourceUrl + "#start=" + startLine + "," + startColumn
                + ";end=" + endLine + "," + endColumn;
        String path = "/sap/bc/adt/refactorings"
                + "?step=evaluate"
                + "&rel=" + urlEncode("http://www.sap.com/adt/relations/refactoring/extractmethod")
                + "&uri=" + urlEncode(uriFragment);

        HttpResponse<String> response = client.postWithHeaders(path, "", "application/*", "application/*",
                java.util.Map.of());

        JsonObject output = new JsonObject();
        output.addProperty("note", "Read-only evaluation. This MCP does not apply the extraction -- review "
                + "rawResponse and perform the actual refactor manually (e.g. via sap_set_source) if appropriate.");
        output.addProperty("rawResponse", response.body());
        return output.toString();
    }
}
