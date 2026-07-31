package com.sap.adt.mcp.tools;

import java.net.http.HttpResponse;

import com.google.gson.JsonObject;
import com.sap.adt.mcp.sap.AdtRestClient;

/**
 * Tool: sap_evaluate_rename -- Impact analysis (read-only, dry-run) for renaming an ABAP
 * identifier at a given source position: what would be affected if you renamed it.
 *
 * Deliberately stops at the "evaluate" step. The ADT rename refactoring is a 3-step
 * protocol (evaluate -> preview -> execute) and the preview/execute steps require posting
 * back a generated XML body (oldName/newName/affectedObjects/...) whose exact schema this
 * port could not independently confirm against a real backend -- guessing it risks silently
 * renaming the wrong thing. The evaluate step itself needs no body (only query parameters),
 * so it is safe to expose: it tells you what SAP *would* touch, for manual review/decision,
 * without changing anything. Ported from the abap-adt-api reference client
 * (src/api/refactor.ts, renameEvaluate): POST /sap/bc/adt/refactorings?step=evaluate.
 */
public class EvaluateRenameTool extends AbstractMcpTool {

    public static final String NAME = "sap_evaluate_rename";

    public EvaluateRenameTool(AdtRestClient client) {
        super(client);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return "Read-only impact analysis for renaming an ABAP identifier: shows what objects would be "
                + "affected, without renaming anything. Provide objectType+objectName (or objectSourceUrl) "
                + "and the 1-based line/column range of the identifier to rename.";
    }

    @Override
    public JsonObject getInputSchema() {
        JsonObject lineProp = new JsonObject();
        lineProp.addProperty("type", "integer");
        lineProp.addProperty("description", "1-based source line of the identifier.");

        JsonObject startColProp = new JsonObject();
        startColProp.addProperty("type", "integer");
        startColProp.addProperty("description", "0-based column where the identifier starts.");

        JsonObject endColProp = new JsonObject();
        endColProp.addProperty("type", "integer");
        endColProp.addProperty("description", "0-based column where the identifier ends.");

        JsonObject properties = new JsonObject();
        properties.add("objectType", AdtUrlResolver.buildTypeProperty());
        properties.add("objectName", AdtUrlResolver.buildNameProperty());
        properties.add("line", lineProp);
        properties.add("startColumn", startColProp);
        properties.add("endColumn", endColProp);

        com.google.gson.JsonArray required = new com.google.gson.JsonArray();
        required.add("line");
        required.add("startColumn");
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
        if (!arguments.has("line") || !arguments.has("startColumn") || !arguments.has("endColumn")) {
            throw new IllegalArgumentException("Provide line, startColumn and endColumn.");
        }
        int line = arguments.get("line").getAsInt();
        int startColumn = arguments.get("startColumn").getAsInt();
        int endColumn = arguments.get("endColumn").getAsInt();

        String uriFragment = sourceUrl + "#start=" + line + "," + startColumn + ";end=" + line + "," + endColumn;
        String path = "/sap/bc/adt/refactorings"
                + "?step=evaluate"
                + "&rel=" + urlEncode("http://www.sap.com/adt/relations/refactoring/rename")
                + "&uri=" + urlEncode(uriFragment);

        HttpResponse<String> response = client.postWithHeaders(path, "", "application/*", "application/*",
                java.util.Map.of());

        JsonObject output = new JsonObject();
        output.addProperty("note", "Read-only evaluation. This MCP does not apply renames -- review "
                + "rawResponse and perform the actual rename manually (e.g. via sap_set_source) if appropriate.");
        output.addProperty("rawResponse", response.body());
        return output.toString();
    }
}
