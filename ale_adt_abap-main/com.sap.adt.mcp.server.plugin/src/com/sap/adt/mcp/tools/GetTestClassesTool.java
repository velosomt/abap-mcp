package com.sap.adt.mcp.tools;

import java.net.http.HttpResponse;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sap.adt.mcp.sap.AdtRestClient;

/**
 * Tool: sap_get_test_classes -- Read the ABAP Unit test include (/includes/testclasses)
 * of an existing class, trying the active version first and falling back to the inactive
 * (unreleased) version if active is missing/empty.
 *
 * Ported from the AWS SAP ABAP Accelerator MCP (get_test_classes), which does the same
 * active-then-inactive fallback against the same endpoint. The generic sap_get_source
 * escape hatch (objectSourceUrl pointing at .../includes/testclasses) can read one version
 * at a time; this tool does the active/inactive fallback automatically.
 */
public class GetTestClassesTool extends AbstractMcpTool {

    public static final String NAME = "sap_get_test_classes";

    public GetTestClassesTool(AdtRestClient client) {
        super(client);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return "Read the ABAP Unit test class source (/includes/testclasses) of a CLAS. Tries the active "
                + "version first, then the inactive version if active is missing or empty.";
    }

    @Override
    public JsonObject getInputSchema() {
        JsonObject classNameProp = new JsonObject();
        classNameProp.addProperty("type", "string");
        classNameProp.addProperty("description", "Class name (e.g. 'ZCL_MY_CLASS'). Case-insensitive.");

        JsonObject properties = new JsonObject();
        properties.add("className", classNameProp);

        JsonArray required = new JsonArray();
        required.add("className");

        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.add("properties", properties);
        schema.add("required", required);

        return schema;
    }

    @Override
    public String execute(JsonObject arguments) throws Exception {
        if (!arguments.has("className") || arguments.get("className").isJsonNull()) {
            throw new IllegalArgumentException("Provide 'className'.");
        }
        String className = arguments.get("className").getAsString().toLowerCase();
        String baseUrl = "/sap/bc/adt/oo/classes/" + className + "/includes/testclasses";

        String activeSource = tryGetVersion(baseUrl, "active");
        if (activeSource != null && !activeSource.trim().isEmpty()) {
            JsonObject output = new JsonObject();
            output.addProperty("className", className.toUpperCase());
            output.addProperty("version", "active");
            output.addProperty("source", activeSource);
            return output.toString();
        }

        String inactiveSource = tryGetVersion(baseUrl, "inactive");
        if (inactiveSource != null && !inactiveSource.trim().isEmpty()) {
            JsonObject output = new JsonObject();
            output.addProperty("className", className.toUpperCase());
            output.addProperty("version", "inactive");
            output.addProperty("source", inactiveSource);
            return output.toString();
        }

        JsonObject output = new JsonObject();
        output.addProperty("className", className.toUpperCase());
        output.addProperty("found", false);
        output.addProperty("note", "No test classes found (active or inactive). Use "
                + "sap_create_or_update_test_class to create one.");
        return output.toString();
    }

    private String tryGetVersion(String baseUrl, String version) {
        try {
            HttpResponse<String> response = client.get(baseUrl + "?version=" + version, "text/plain");
            if (response.statusCode() == 200) {
                return response.body();
            }
        } catch (Exception e) {
            // Fall through -- caller tries the next version (or reports not-found).
        }
        return null;
    }
}
