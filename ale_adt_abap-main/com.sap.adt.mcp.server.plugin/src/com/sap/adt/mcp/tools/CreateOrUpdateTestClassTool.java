package com.sap.adt.mcp.tools;

import java.net.http.HttpResponse;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.sap.adt.mcp.sap.AdtRestClient;
import com.sap.adt.mcp.sap.AdtXmlParser;

/**
 * Tool: sap_create_or_update_test_class -- Create or overwrite the ABAP Unit test include
 * (/includes/testclasses) of an existing class from a list of test method names +
 * implementations, generating the LTC class skeleton automatically.
 *
 * Ported from the AWS SAP ABAP Accelerator MCP (create_or_update_test_class /
 * service_binding... no -- class_handler.create_test_class). Two details matter here and
 * were the actual gap versus our previous generic sap_set_source escape hatch:
 * (1) AWS locks the *main class* object (.../oo/classes/{name}), not the testclasses
 *     sub-include URL -- ADT lock semantics are per top-level object, locking the
 *     sub-include directly is not guaranteed to work on every backend.
 * (2) If the testclasses include doesn't exist yet, AWS creates it first (POST .../includes
 *     with a class:abapClassInclude body) before writing source into it -- a first-time
 *     PUT against a non-existent include can otherwise fail outright.
 */
public class CreateOrUpdateTestClassTool extends AbstractMcpTool {

    public static final String NAME = "sap_create_or_update_test_class";

    public CreateOrUpdateTestClassTool(AdtRestClient client) {
        super(client);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return "Create or overwrite the ABAP Unit test class (/includes/testclasses) of an existing CLAS, "
                + "generating the LTC DEFINITION/IMPLEMENTATION skeleton from 'methods'. Locks the main class, "
                + "creates the testclasses include if missing, writes the source, unlocks, then activates the class.";
    }

    @Override
    public JsonObject getInputSchema() {
        JsonObject methodNameProp = new JsonObject();
        methodNameProp.addProperty("type", "string");
        methodNameProp.addProperty("description", "Test method name (e.g. 'test_calculate_total').");

        JsonObject methodImplProp = new JsonObject();
        methodImplProp.addProperty("type", "string");
        methodImplProp.addProperty("description",
                "ABAP statements for the method body (without METHOD/ENDMETHOD). If omitted, a "
                + "placeholder comment is generated instead.");

        JsonObject methodProperties = new JsonObject();
        methodProperties.add("name", methodNameProp);
        methodProperties.add("implementation", methodImplProp);

        JsonArray methodRequired = new JsonArray();
        methodRequired.add("name");

        JsonObject methodSchema = new JsonObject();
        methodSchema.addProperty("type", "object");
        methodSchema.add("properties", methodProperties);
        methodSchema.add("required", methodRequired);

        JsonObject methodsProp = new JsonObject();
        methodsProp.addProperty("type", "array");
        methodsProp.addProperty("description", "Test methods to generate inside the local test class.");
        methodsProp.add("items", methodSchema);

        JsonObject classNameProp = new JsonObject();
        classNameProp.addProperty("type", "string");
        classNameProp.addProperty("description", "Class name the test include belongs to (e.g. 'ZCL_MY_CLASS').");

        JsonObject transportProp = new JsonObject();
        transportProp.addProperty("type", "string");
        transportProp.addProperty("description", "Optional transport request number.");

        JsonObject properties = new JsonObject();
        properties.add("className", classNameProp);
        properties.add("methods", methodsProp);
        properties.add("transport", transportProp);

        JsonArray required = new JsonArray();
        required.add("className");
        required.add("methods");

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
        if (!arguments.has("methods") || !arguments.get("methods").isJsonArray()
                || arguments.getAsJsonArray("methods").size() == 0) {
            throw new IllegalArgumentException("Provide 'methods': a non-empty list of {name, implementation?}.");
        }

        String classNameUpper = arguments.get("className").getAsString().toUpperCase();
        String classNameLower = classNameUpper.toLowerCase();
        String transport = optString(arguments, "transport");
        JsonArray methods = arguments.getAsJsonArray("methods");

        String testClassSource = generateTestClassSource(classNameLower, methods);

        String classUrl = "/sap/bc/adt/oo/classes/" + classNameLower;
        String testClassesUrl = classUrl + "/includes/testclasses";

        // IMPORTANT: probe whether the testclasses include exists BEFORE locking. This GET is
        // stateless; if issued between the stateful lock and the PUT, the backend drops the
        // stateful session (and the lock with it) -> HTTP 423 "include not locked". Doing it
        // first keeps lock + PUT consecutive within the same stateful session.
        boolean testClassesExists = checkTestClassesExists(testClassesUrl);

        // Lock the main class object (not the testclasses sub-include -- see class javadoc).
        String lockPath = classUrl + "?_action=LOCK&accessMode=MODIFY";
        HttpResponse<String> lockResp = client.postWithHeaders(lockPath, "",
                "application/*",
                "application/vnd.sap.as+xml;charset=UTF-8;dataname=com.sap.adt.lock.result;q=0.8, "
                + "application/vnd.sap.as+xml;charset=UTF-8;dataname=com.sap.adt.lock.result2;q=0.9",
                STATEFUL_HEADERS);
        String lockHandle = AdtXmlParser.extractLockHandle(lockResp.body());
        if (lockHandle == null || lockHandle.isEmpty()) {
            throw new IllegalStateException("Failed to lock class " + classNameUpper
                    + " for test class update. Response: " + lockResp.body());
        }

        JsonObject output = new JsonObject();
        output.addProperty("className", classNameUpper);

        try {
            if (!testClassesExists) {
                createTestClassesInclude(classUrl, lockHandle);
            }

            String writePath = testClassesUrl + "?lockHandle=" + urlEncode(lockHandle);
            if (transport != null && !transport.isEmpty()) {
                writePath = writePath + "&corrNr=" + urlEncode(transport);
            }
            HttpResponse<String> writeResp = client.putWithHeaders(writePath, testClassSource,
                    "text/plain; charset=utf-8", STATEFUL_HEADERS);

            boolean updated = writeResp.statusCode() == 200 || writeResp.statusCode() == 204;
            output.addProperty("updated", updated);
            output.addProperty("source", testClassSource);
            if (!updated) {
                output.addProperty("writeError", "HTTP " + writeResp.statusCode() + ": " + writeResp.body());
                return output.toString();
            }
        } finally {
            safeUnlock(classUrl, lockHandle);
        }

        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        try {
            String activateXml = "<adtcore:objectReferences xmlns:adtcore=\"http://www.sap.com/adt/core\">"
                    + "<adtcore:objectReference adtcore:uri=\"" + escapeXml(classUrl)
                    + "\" adtcore:name=\"" + escapeXml(classNameUpper) + "\"/>"
                    + "</adtcore:objectReferences>";

            HttpResponse<String> activateResp = client.post(
                    "/sap/bc/adt/activation?method=activate&preauditRequested=true",
                    activateXml,
                    "application/xml",
                    "application/xml,application/vnd.sap.adt.inactivectsobjects.v1+xml;q=0.9");

            JsonObject activationResult = AdtXmlParser.parseActivationResult(activateResp.body());
            boolean activated = activationResult.has("success") && activationResult.get("success").getAsBoolean();
            output.addProperty("activated", activated);
            output.add("activationMessages", activationResult.get("messages"));
        } catch (Exception e) {
            // Mirrors AWS: the test class source is already saved and usable even if
            // activation fails or errors out -- never fail the whole call because of this.
            output.addProperty("activated", false);
            output.addProperty("activationError", e.getMessage()
                    + " (test class source was saved successfully and may still be usable).");
        }

        return output.toString();
    }

    private boolean checkTestClassesExists(String testClassesUrl) {
        try {
            HttpResponse<String> response = client.get(testClassesUrl, "text/plain");
            return response.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    private void createTestClassesInclude(String classUrl, String lockHandle) {
        String createXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<class:abapClassInclude xmlns:adtcore=\"http://www.sap.com/adt/core\" "
                + "xmlns:class=\"http://www.sap.com/adt/oo/classes\" adtcore:name=\"testclasses\" "
                + "class:includeType=\"testclasses\"/>";
        String createPath = classUrl + "/includes?lockHandle=" + urlEncode(lockHandle);
        try {
            client.postWithHeaders(createPath, createXml,
                    "application/vnd.sap.adt.oo.classincludes+xml",
                    "application/vnd.sap.adt.oo.classincludes+xml",
                    STATEFUL_HEADERS);
        } catch (Exception e) {
            // Advisory only, same as AWS: the include may already exist (race) or be
            // created implicitly by the PUT below -- never block on this step failing.
        }
    }

    private void safeUnlock(String classUrl, String lockHandle) {
        try {
            String unlockPath = classUrl + "?_action=UNLOCK&lockHandle=" + urlEncode(lockHandle);
            client.postWithHeaders(unlockPath, "", "application/*", "application/*", STATEFUL_HEADERS);
        } catch (Exception e) {
            // Ignore -- best-effort cleanup.
        }
    }

    private String generateTestClassSource(String classNameLower, JsonArray methods) {
        StringBuilder sb = new StringBuilder();
        sb.append("*\"* Local Test Class for ").append(classNameLower).append("\n");
        sb.append("CLASS ltc_").append(classNameLower)
                .append(" DEFINITION FOR TESTING DURATION SHORT RISK LEVEL HARMLESS.\n");
        sb.append("  PRIVATE SECTION.\n");
        sb.append("    METHODS:\n");

        for (int i = 0; i < methods.size(); i++) {
            JsonObject method = methods.get(i).getAsJsonObject();
            String name = method.get("name").getAsString();
            String comma = (i < methods.size() - 1) ? "," : ".";
            sb.append("      ").append(name).append(" FOR TESTING").append(comma).append("\n");
        }

        sb.append("ENDCLASS.\n\n");
        sb.append("CLASS ltc_").append(classNameLower).append(" IMPLEMENTATION.\n");

        for (JsonElement el : methods) {
            JsonObject method = el.getAsJsonObject();
            String name = method.get("name").getAsString();
            String implementation = method.has("implementation") && !method.get("implementation").isJsonNull()
                    ? method.get("implementation").getAsString() : "\" Test implementation";
            sb.append("  METHOD ").append(name).append(".\n");
            sb.append("    ").append(implementation).append("\n");
            sb.append("  ENDMETHOD.\n\n");
        }

        sb.append("ENDCLASS.");
        return sb.toString();
    }
}
