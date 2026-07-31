package com.sap.adt.mcp.tools;

import java.net.http.HttpResponse;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sap.adt.mcp.sap.AdtRestClient;
import com.sap.adt.mcp.sap.AdtXmlParser;

/**
 * Tool: sap_transport_check -- Ask SAP (CTS) which transport requests are
 * available/required for an object + package combination, before creating or
 * changing it. Real ADT endpoint (/sap/bc/adt/cts/transportchecks) -- never
 * returns fabricated data; if SAP errors out, this tool fails loudly instead
 * of inventing a transport number.
 */
public class TransportCheckTool extends AbstractMcpTool {

    public static final String NAME = "sap_transport_check";

    public TransportCheckTool(AdtRestClient client) {
        super(client);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return "Check which transport request(s) (TRKORR) are available/required for an object in a package, "
                + "via SAP's CTS transport check. Useful before sap_create_object or sap_set_source on a "
                + "transportable package (not needed for $TMP).";
    }

    @Override
    public JsonObject getInputSchema() {
        JsonObject properties = new JsonObject();
        properties.add("objectType", AdtUrlResolver.buildTypeProperty());
        properties.add("objectName", AdtUrlResolver.buildNameProperty());

        JsonObject packageProp = new JsonObject();
        packageProp.addProperty("type", "string");
        packageProp.addProperty("description", "Target package (DEVCLASS), e.g. ZMY_PACKAGE. Not needed for $TMP.");
        properties.add("packageName", packageProp);

        JsonObject operationProp = new JsonObject();
        operationProp.addProperty("type", "string");
        operationProp.addProperty("description", "CTS operation: I = insert (new object), U = update (existing object). Default: I.");
        properties.add("operation", operationProp);

        JsonArray required = new JsonArray();
        required.add("objectType");
        required.add("objectName");
        required.add("packageName");

        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.add("properties", properties);
        schema.add("required", required);

        return schema;
    }

    @Override
    public String execute(JsonObject arguments) throws Exception {
        String objectUrl = resolveObjectUrlArg(arguments, "objectUrl");
        if (objectUrl == null) {
            throw new IllegalArgumentException("Provide objectType + objectName.");
        }

        String packageName = optString(arguments, "packageName");
        if (packageName == null || packageName.isEmpty()) {
            throw new IllegalArgumentException("packageName (DEVCLASS) is required.");
        }

        String operation = optString(arguments, "operation");
        if (operation == null || operation.isEmpty()) operation = "I";

        String checkXml = "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>"
                + "<asx:abap version=\"1.0\" xmlns:asx=\"http://www.sap.com/abapxml\">"
                + "<asx:values>"
                + "<DATA>"
                + "<PGMID></PGMID>"
                + "<OBJECT></OBJECT>"
                + "<OBJECTNAME></OBJECTNAME>"
                + "<DEVCLASS>" + escapeXml(packageName) + "</DEVCLASS>"
                + "<SUPER_PACKAGE></SUPER_PACKAGE>"
                + "<RECORD_CHANGES></RECORD_CHANGES>"
                + "<OPERATION>" + escapeXml(operation) + "</OPERATION>"
                + "<URI>" + escapeXml(objectUrl) + "</URI>"
                + "</DATA>"
                + "</asx:values>"
                + "</asx:abap>";

        String mediaType = "application/vnd.sap.as+xml; charset=UTF-8; dataname=com.sap.adt.transport.service.checkData";

        HttpResponse<String> response = client.post(
                "/sap/bc/adt/cts/transportchecks", checkXml, mediaType, mediaType);

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Transport check failed: HTTP " + response.statusCode()
                    + " - " + response.body());
        }

        JsonObject result = AdtXmlParser.parseTransportCheck(response.body());
        result.addProperty("statusCode", response.statusCode());
        return result.toString();
    }
}
