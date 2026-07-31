package com.sap.adt.mcp.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sap.adt.mcp.sap.AdtRestClient;

/**
 * Tool: sap_set_source -- Write ABAP source code.
 */
public class SetSourceTool extends AbstractMcpTool {

    public static final String NAME = "sap_set_source";

    public SetSourceTool(AdtRestClient client) {
        super(client);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return "Write ABAP source code to existing object. Locks, writes, unlocks, and activates automatically.";
    }

    @Override
    public JsonObject getInputSchema() {
        JsonObject sourceProp = new JsonObject();
        sourceProp.addProperty("type", "string");
        sourceProp.addProperty("description", "CRITICAL WARNING: The COMPLETE ABAP source code to write. YOU MUST PROVIDE THE ENTIRE FILE CONTENT. DO NOT OMIT CODE OR USE PLACEHOLDERS LIKE '// rest of code'. IF YOU DO, YOU WILL DELETE ALL THE USER'S CODE IN SAP. For partial edits, use sap_replace_source_content instead!");

        JsonObject transportProp = new JsonObject();
        transportProp.addProperty("type", "string");
        transportProp.addProperty("description", "Optional transport request number (e.g. 'DEVK900123')");

        JsonObject properties = new JsonObject();
        properties.add("objectType", AdtUrlResolver.buildTypeProperty());
        properties.add("objectName", AdtUrlResolver.buildNameProperty());
        properties.add("source", sourceProp);
        properties.add("transport", transportProp);

        JsonArray required = new JsonArray();
        required.add("objectType");
        required.add("objectName");
        required.add("source");

        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.add("properties", properties);
        schema.add("required", required);

        return schema;
    }

    @Override
    public String execute(JsonObject arguments) throws Exception {
        String objectSourceUrl = resolveSourceUrlArg(arguments, "objectSourceUrl");
        if (objectSourceUrl == null) {
            throw new IllegalArgumentException("Provide either objectType + objectName, or objectSourceUrl.");
        }
        objectSourceUrl = ensureSourceUrl(objectSourceUrl);
        String source = arguments.get("source").getAsString();
        String transport = optString(arguments, "transport");

        if (isFunctionModuleUrl(objectSourceUrl)) {
            source = sanitizeFmSource(source);
        }

        return AdtSourceWriter.lockWriteUnlock(client, objectSourceUrl, source, transport);
    }
}
