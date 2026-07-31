package com.sap.adt.mcp.tools;

import java.net.http.HttpResponse;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sap.adt.mcp.sap.AdtRestClient;
import com.sap.adt.mcp.sap.AdtXmlParser;

/**
 * Tool: sap_set_text_elements -- Write the text elements (text symbols, selection texts,
 * list headers) of a PROG/CLAS/FUGR.
 *
 * Same two protocol details as sap_create_or_update_test_class, because text elements are
 * also a sub-resource of the main object: (1) the lock is taken on the *main object*
 * (.../programs/programs/{name}, .../oo/classes/{name} or .../functions/groups/{name}),
 * not on the textelements sub-URL; (2) the PUT body is plain text in the line-based format
 * documented by sap_get_text_elements, not XML. Caller is responsible for producing a
 * syntactically valid text-elements body (e.g. read the current one with sap_get_text_elements
 * first and edit it) -- this tool does not validate or reformat it.
 */
public class SetTextElementsTool extends AbstractMcpTool {

    public static final String NAME = "sap_set_text_elements";

    public SetTextElementsTool(AdtRestClient client) {
        super(client);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return "Write the text elements (text symbols, selection texts, list headers) of a PROG, CLAS or "
                + "FUGR. Locks the main object, writes the text-element source, unlocks, then activates.";
    }

    @Override
    public JsonObject getInputSchema() {
        JsonObject objectTypeProp = new JsonObject();
        objectTypeProp.addProperty("type", "string");
        objectTypeProp.addProperty("description", "PROG/P, CLAS/OC or FUGR/F.");

        JsonObject categoryProp = new JsonObject();
        categoryProp.addProperty("type", "string");
        categoryProp.addProperty("description", "Text element category, e.g. 'selections' or 'header'.");

        JsonObject sourceProp = new JsonObject();
        sourceProp.addProperty("type", "string");
        sourceProp.addProperty("description", "Full text-element body in the line-based format returned "
                + "by sap_get_text_elements (not XML, not a diff -- this overwrites the whole category).");

        JsonObject transportProp = new JsonObject();
        transportProp.addProperty("type", "string");
        transportProp.addProperty("description", "Optional transport request number.");

        JsonObject properties = new JsonObject();
        properties.add("objectType", objectTypeProp);
        properties.add("objectName", AdtUrlResolver.buildNameProperty());
        properties.add("category", categoryProp);
        properties.add("source", sourceProp);
        properties.add("transport", transportProp);

        JsonArray required = new JsonArray();
        required.add("objectType");
        required.add("objectName");
        required.add("category");
        required.add("source");

        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.add("properties", properties);
        schema.add("required", required);

        return schema;
    }

    @Override
    public String execute(JsonObject arguments) throws Exception {
        String objectType = optString(arguments, "objectType");
        String objectName = optString(arguments, "objectName");
        String category = optString(arguments, "category");
        String source = optString(arguments, "source");
        String transport = optString(arguments, "transport");
        if (objectType == null || objectName == null || category == null || source == null) {
            throw new IllegalArgumentException("Provide objectType, objectName, category and source.");
        }
        if (!GetTextElementsTool.isValidCategory(category)) {
            throw new IllegalArgumentException("category must contain only letters, digits, '_' or '-'.");
        }

        String lockUrl = GetTextElementsTool.lockObjectUrl(objectType, objectName);
        String baseUrl = GetTextElementsTool.textElementsBaseUrl(objectType, objectName);
        if (lockUrl == null || baseUrl == null) {
            throw new IllegalArgumentException("objectType must be PROG/P, CLAS/OC or FUGR/F.");
        }
        String textElementsUrl = baseUrl + "/source/" + urlEncode(category);

        String lockPath = lockUrl + "?_action=LOCK&accessMode=MODIFY";
        HttpResponse<String> lockResp = client.postWithHeaders(lockPath, "",
                "application/*",
                "application/vnd.sap.as+xml;charset=UTF-8;dataname=com.sap.adt.lock.result;q=0.8, "
                + "application/vnd.sap.as+xml;charset=UTF-8;dataname=com.sap.adt.lock.result2;q=0.9",
                STATEFUL_HEADERS);
        String lockHandle = AdtXmlParser.extractLockHandle(lockResp.body());
        if (lockHandle == null || lockHandle.isEmpty()) {
            throw new IllegalStateException("Failed to lock " + objectName + " for text element update. "
                    + "Response: " + lockResp.body());
        }

        JsonObject output = new JsonObject();
        output.addProperty("objectName", objectName.toUpperCase());
        output.addProperty("category", category);

        try {
            String writePath = textElementsUrl + "?lockHandle=" + urlEncode(lockHandle);
            if (transport != null && !transport.isEmpty()) {
                writePath = writePath + "&corrNr=" + urlEncode(transport);
            }
            HttpResponse<String> writeResp = client.putWithHeaders(writePath, source,
                    "application/vnd.sap.adt.textelements." + category + ".v1; charset=utf-8",
                    STATEFUL_HEADERS);

            boolean updated = writeResp.statusCode() == 200 || writeResp.statusCode() == 204;
            output.addProperty("updated", updated);
            if (!updated) {
                output.addProperty("writeError", "HTTP " + writeResp.statusCode() + ": " + writeResp.body());
                return output.toString();
            }
        } finally {
            try {
                String unlockPath = lockUrl + "?_action=UNLOCK&lockHandle=" + urlEncode(lockHandle);
                client.postWithHeaders(unlockPath, "", "application/*", "application/*", STATEFUL_HEADERS);
            } catch (Exception e) {
                // Best-effort cleanup, same as the other lock-based tools in this codebase.
            }
        }

        try {
            String activateXml = "<adtcore:objectReferences xmlns:adtcore=\"http://www.sap.com/adt/core\">"
                    + "<adtcore:objectReference adtcore:uri=\"" + escapeXml(lockUrl)
                    + "\" adtcore:name=\"" + escapeXml(objectName.toUpperCase()) + "\"/>"
                    + "</adtcore:objectReferences>";
            HttpResponse<String> activateResp = client.post(
                    "/sap/bc/adt/activation?method=activate&preauditRequested=true",
                    activateXml, "application/xml",
                    "application/xml,application/vnd.sap.adt.inactivectsobjects.v1+xml;q=0.9");
            JsonObject activationResult = AdtXmlParser.parseActivationResult(activateResp.body());
            output.addProperty("activated", activationResult.has("success")
                    && activationResult.get("success").getAsBoolean());
        } catch (Exception e) {
            output.addProperty("activated", false);
            output.addProperty("activationError", e.getMessage());
        }

        return output.toString();
    }
}
