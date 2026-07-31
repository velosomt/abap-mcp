package com.sap.adt.mcp.tools;

import java.net.http.HttpResponse;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sap.adt.mcp.sap.AdtRestClient;

/**
 * Tool: sap_create_transport -- Create a new (empty) transport/correction request.
 * Non-destructive: it only opens a new request; it never releases or imports anything.
 */
public class CreateTransportTool extends AbstractMcpTool {

    public static final String NAME = "sap_create_transport";

    public CreateTransportTool(AdtRestClient client) {
        super(client);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return "Creates a new transport/correction request (POST /sap/bc/adt/cts/transports) and returns its number. "
            + "Non-destructive: it only OPENS a request, it never releases, deletes or imports anything. Provide "
            + "'description' (the request text) and 'packageName' (DEVCLASS). Optionally 'objectUri' as the reference "
            + "object (REF) the request is opened for, and 'operation' (default 'I'). Note: on systems with a custom "
            + "transport-strategy exit, opening a request over ADT may still be blocked server-side.";
    }

    @Override
    public JsonObject getInputSchema() {
        JsonObject descProp = new JsonObject();
        descProp.addProperty("type", "string");
        descProp.addProperty("description", "Request text / short description of the transport.");

        JsonObject pkgProp = new JsonObject();
        pkgProp.addProperty("type", "string");
        pkgProp.addProperty("description", "Target package (DEVCLASS) the request is for.");

        JsonObject refProp = new JsonObject();
        refProp.addProperty("type", "string");
        refProp.addProperty("description", "Optional reference object URI (REF), e.g. an object's ADT URL.");

        JsonObject opProp = new JsonObject();
        opProp.addProperty("type", "string");
        opProp.addProperty("description", "Optional operation code. Default 'I'.");

        JsonObject properties = new JsonObject();
        properties.add("description", descProp);
        properties.add("packageName", pkgProp);
        properties.add("objectUri", refProp);
        properties.add("operation", opProp);

        JsonArray required = new JsonArray();
        required.add("description");
        required.add("packageName");

        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.add("properties", properties);
        schema.add("required", required);
        return schema;
    }

    @Override
    public String execute(JsonObject arguments) throws Exception {
        String description = optString(arguments, "description");
        String pkg = optString(arguments, "packageName");
        if (description == null || description.isEmpty() || pkg == null || pkg.isEmpty()) {
            throw new IllegalArgumentException("Provide 'description' and 'packageName'.");
        }
        if (description.length() > 60) description = description.substring(0, 60);
        String ref = optString(arguments, "objectUri");
        String operation = optString(arguments, "operation");
        if (operation == null || operation.isEmpty()) operation = "I";

        String body = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<asx:abap xmlns:asx=\"http://www.sap.com/abapxml\" version=\"1.0\"><asx:values><DATA>"
                + "<OPERATION>" + escapeXml(operation) + "</OPERATION>"
                + "<DEVCLASS>" + escapeXml(pkg.toUpperCase()) + "</DEVCLASS>"
                + "<REQUEST_TEXT>" + escapeXml(description) + "</REQUEST_TEXT>"
                + "<REF>" + escapeXml(ref == null ? "" : ref) + "</REF>"
                + "</DATA></asx:values></asx:abap>";

        String contentType = "application/vnd.sap.as+xml; charset=UTF-8; dataname=com.sap.adt.CreateCorrectionRequest";
        HttpResponse<String> response = client.post("/sap/bc/adt/cts/transports", body, contentType, "text/plain");

        String respBody = response.body() == null ? "" : response.body().trim();
        String number = respBody.contains("/") ? respBody.substring(respBody.lastIndexOf('/') + 1) : respBody;

        JsonObject output = new JsonObject();
        output.addProperty("statusCode", response.statusCode());
        output.addProperty("transport", number);
        output.addProperty("response", respBody);
        return output.toString();
    }
}
