package com.sap.adt.mcp.tools;

import java.net.http.HttpResponse;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.sap.adt.mcp.model.AdtObjectDefinition;
import com.sap.adt.mcp.registry.AdtObjectRegistry;
import com.sap.adt.mcp.sap.AdtRestClient;
import com.sap.adt.mcp.sap.AdtXmlParser;

/**
 * Tool: sap_execute_abap -- Execute arbitrary ABAP code via a unit test wrapper.
 *
 * Strategy (ported from vibing-steampunk vsp):
 *   1. Create a temporary class in $TMP with a FOR TESTING method
 *   2. The method runs the user's code and calls cl_abap_unit_assert=>fail(msg=lv_result)
 *   3. Run unit tests — the assertion failure message carries lv_result as output
 *   4. Parse and return the output
 *   5. Delete the temporary class
 *
 * The user assigns to DATA lv_result TYPE string to return output.
 */
public class ExecuteAbapTool extends AbstractMcpTool {

    public static final String NAME = "sap_execute_abap";

    private static final String CLASS_PREFIX = "ZTMP_EXEC_";
    private static final String CLASS_URL_BASE = "/sap/bc/adt/oo/classes";

    public ExecuteAbapTool(AdtRestClient client) {
        super(client);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return "Execute arbitrary ABAP code in a temporary class and return output. "
                + "Assign the result to DATA lv_result TYPE string. "
                + "The class is created in $TMP, executed, and deleted automatically. "
                + "Risk levels: harmless (default, read-only), dangerous (can write DB), critical (full access).";
    }

    @Override
    public JsonObject getInputSchema() {
        JsonObject codeProp = new JsonObject();
        codeProp.addProperty("type", "string");
        codeProp.addProperty("description",
                "ABAP code to execute. Assign to lv_result to return output. "
                + "Example: lv_result = sy-sysid.");

        JsonObject riskProp = new JsonObject();
        riskProp.addProperty("type", "string");
        JsonArray riskEnum = new JsonArray();
        riskEnum.add("harmless"); riskEnum.add("dangerous"); riskEnum.add("critical");
        riskProp.add("enum", riskEnum);
        riskProp.addProperty("description", "Risk level. Default: harmless");

        JsonObject keepProp = new JsonObject();
        keepProp.addProperty("type", "boolean");
        keepProp.addProperty("description", "Keep the temp class after execution (for debugging). Default: false");

        JsonObject properties = new JsonObject();
        properties.add("code", codeProp);
        properties.add("riskLevel", riskProp);
        properties.add("keepClass", keepProp);

        JsonArray required = new JsonArray();
        required.add("code");

        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.add("properties", properties);
        schema.add("required", required);
        return schema;
    }

    @Override
    public String execute(JsonObject arguments) throws Exception {
        String userCode = arguments.get("code").getAsString();
        String riskLevel = optString(arguments, "riskLevel");
        if (riskLevel == null || riskLevel.isEmpty()) riskLevel = "harmless";
        boolean keepClass = arguments.has("keepClass") && arguments.get("keepClass").getAsBoolean();

        String timestamp = Long.toString(System.currentTimeMillis() % 100000000L);
        String className = (CLASS_PREFIX + timestamp).toLowerCase();
        String classUrl = CLASS_URL_BASE + "/" + className;
        String sourceUrl = classUrl + "/source/main";

        String classSrc = buildClassSource(className.toUpperCase(), userCode, riskLevel);

        // 1. Create class -- same endpoint/content-type/XML shape as CreateObjectTool's
        // registered CLAS/OC definition (AdtObjectRegistry). Diverging from that
        // (e.g. the old classlibrary.v2 media type + trailing-slash URL previously
        // used here) returns HTTP 404 on this ADT backend.
        AdtObjectDefinition classDef = AdtObjectRegistry.getInstance().getDefinition("CLAS/OC");
        String language = client.getLanguage();
        String createXml = classDef.getTemplateContent()
                .replace("${name}", escapeXml(className.toUpperCase()))
                .replace("${description}", "Temp execution class")
                .replace("${packageName}", "$TMP")
                .replace("${language}", escapeXml(language));

        try {
            HttpResponse<String> createResp = client.postWithHeaders(
                    classDef.getCreationUrl(),
                    createXml,
                    classDef.getContentType(),
                    classDef.getContentType() + ", application/xml",
                    STATEFUL_HEADERS);

            if (createResp.statusCode() >= 400) {
                throw new RuntimeException("Failed to create temp class (HTTP "
                        + createResp.statusCode() + "): " + createResp.body());
            }

            // 2. Write source
            AdtSourceWriter.lockWriteUnlock(client, sourceUrl, classSrc, null);

            // 3. Run unit tests
            String objectUri = classUrl;
            String xmlBody = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                    + "<aunit:runConfiguration xmlns:aunit=\"http://www.sap.com/adt/aunit\">"
                    + "<external><coverage active=\"false\"/></external>"
                    + "<options>"
                    + "<uriType value=\"semantic\"/>"
                    + "<testDeterminationStrategy sameProgram=\"true\" assignedTests=\"false\"/>"
                    + "<testRiskLevels harmless=\"true\" dangerous=\"true\" critical=\"true\"/>"
                    + "<testDurations short=\"true\" medium=\"true\" long=\"true\"/>"
                    + "<withNavigationUri enabled=\"false\"/>"
                    + "</options>"
                    + "<adtcore:objectSets xmlns:adtcore=\"http://www.sap.com/adt/core\">"
                    + "<objectSet kind=\"inclusive\">"
                    + "<adtcore:objectReferences>"
                    + "<adtcore:objectReference adtcore:uri=\"" + escapeXml(objectUri) + "\"/>"
                    + "</adtcore:objectReferences>"
                    + "</objectSet>"
                    + "</adtcore:objectSets>"
                    + "</aunit:runConfiguration>";

            HttpResponse<String> testResp = client.post("/sap/bc/adt/abapunit/testruns",
                    xmlBody, "application/*", "application/*");

            JsonObject testResult = AdtXmlParser.parseUnitTestResults(testResp.body());
            String output = extractOutput(testResult);

            JsonObject result = new JsonObject();
            result.addProperty("output", output);
            result.addProperty("className", className.toUpperCase());
            result.addProperty("keepClass", keepClass);
            return result.toString();

        } finally {
            if (!keepClass) {
                safeDeleteClass(className, classUrl);
            }
        }
    }

    private static String buildClassSource(String classNameUpper, String userCode, String riskLevel) {
        String risk = riskLevel.toUpperCase();
        return "CLASS " + classNameUpper + " DEFINITION\n"
                + "  PUBLIC\n"
                + "  FINAL\n"
                + "  FOR TESTING\n"
                + "  RISK LEVEL " + risk + "\n"
                + "  DURATION SHORT.\n"
                + "  PUBLIC SECTION.\n"
                + "    METHODS run FOR TESTING.\n"
                + "ENDCLASS.\n\n"
                + "CLASS " + classNameUpper + " IMPLEMENTATION.\n"
                + "  METHOD run.\n"
                + "    DATA lv_result TYPE string.\n"
                + "    " + userCode.replace("\n", "\n    ") + "\n"
                + "    cl_abap_unit_assert=>fail( msg = lv_result ).\n"
                + "  ENDMETHOD.\n"
                + "ENDCLASS.\n";
    }

    private static String extractOutput(JsonObject testResult) {
        // The assertion fail message appears in alerts with kind=failedAssertion
        if (testResult.has("alerts")) {
            JsonArray alerts = testResult.getAsJsonArray("alerts");
            for (JsonElement el : alerts) {
                JsonObject alert = el.getAsJsonObject();
                String kind = alert.has("kind") ? alert.get("kind").getAsString() : "";
                if ("failedAssertion".equalsIgnoreCase(kind) || kind.isEmpty()) {
                    if (alert.has("detail")) {
                        String detail = alert.get("detail").getAsString().trim();
                        if (!detail.isEmpty()) return detail;
                    }
                    if (alert.has("title")) {
                        String title = alert.get("title").getAsString().trim();
                        if (!title.isEmpty()) return title;
                    }
                }
            }
        }
        // Fallback: look inside programs -> testClasses -> methods -> alerts
        if (testResult.has("programs")) {
            for (JsonElement pe : testResult.getAsJsonArray("programs")) {
                JsonObject prog = pe.getAsJsonObject();
                if (!prog.has("testClasses")) continue;
                for (JsonElement ce : prog.getAsJsonArray("testClasses")) {
                    JsonObject tc = ce.getAsJsonObject();
                    if (!tc.has("methods")) continue;
                    for (JsonElement me : tc.getAsJsonArray("methods")) {
                        JsonObject method = me.getAsJsonObject();
                        if (method.has("detail")) return method.get("detail").getAsString();
                    }
                }
            }
        }
        return "(no output — assign to lv_result to return a value)";
    }

    private void safeDeleteClass(String className, String classUrl) {
        try {
            // Lock first
            String lockPath = classUrl + "?_action=LOCK&accessMode=MODIFY";
            HttpResponse<String> lockResp = client.postWithHeaders(lockPath, "",
                    "application/*",
                    "application/vnd.sap.as+xml;charset=UTF-8;dataname=com.sap.adt.lock.result;q=0.8,"
                    + "application/vnd.sap.as+xml;charset=UTF-8;dataname=com.sap.adt.lock.result2;q=0.9",
                    STATEFUL_HEADERS);
            String lockHandle = AdtXmlParser.extractLockHandle(lockResp.body());
            if (lockHandle != null && !lockHandle.isEmpty()) {
                client.deleteWithHeaders(classUrl + "?lockHandle=" + urlEncode(lockHandle),
                        STATEFUL_HEADERS);
            }
        } catch (Exception e) {
            System.err.println("ExecuteAbapTool: could not delete temp class " + className + ": " + e.getMessage());
        }
    }
}
