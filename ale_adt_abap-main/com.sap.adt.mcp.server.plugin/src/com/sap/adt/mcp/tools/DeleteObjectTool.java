package com.sap.adt.mcp.tools;

import java.net.http.HttpResponse;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sap.adt.mcp.sap.AdtRestClient;
import com.sap.adt.mcp.sap.AdtXmlParser;

/**
 * Tool: sap_delete_object -- Delete an ABAP object.
 */
public class DeleteObjectTool extends AbstractMcpTool {

    public static final String NAME = "sap_delete_object";

    public DeleteObjectTool(AdtRestClient client) {
        super(client);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return "Deletes an ABAP object (Class, Program, Interface, etc) from the SAP system.";
    }

    @Override
    public JsonObject getInputSchema() {
        JsonObject transportProp = new JsonObject();
        transportProp.addProperty("type", "string");
        transportProp.addProperty("description", "Optional transport request number (e.g. 'DEVK900123')");

        JsonObject properties = new JsonObject();
        properties.add("objectType", AdtUrlResolver.buildTypeProperty());
        properties.add("objectName", AdtUrlResolver.buildNameProperty());
        properties.add("transport", transportProp);

        JsonArray required = new JsonArray();
        required.add("objectType");
        required.add("objectName");

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
        String transport = optString(arguments, "transport");

        // O ADT exige lock para deletar: bloqueia (sessão stateful), obtém o lockHandle e
        // envia o DELETE com ?lockHandle=... na MESMA sessão stateful. Sem isso o backend
        // devolve HTTP 400 "Parameter lockHandle could not be found".
        String lockPath = objectUrl + "?_action=LOCK&accessMode=MODIFY";
        HttpResponse<String> lockResp = client.postWithHeaders(lockPath, "",
                "application/*",
                "application/vnd.sap.as+xml;charset=UTF-8;dataname=com.sap.adt.lock.result;q=0.8, "
                + "application/vnd.sap.as+xml;charset=UTF-8;dataname=com.sap.adt.lock.result2;q=0.9",
                STATEFUL_HEADERS);
        String lockHandle = AdtXmlParser.extractLockHandle(lockResp.body());
        if (lockHandle == null || lockHandle.isEmpty()) {
            throw new IllegalStateException("Failed to lock object before delete. Response: " + lockResp.body());
        }

        String deletePath = objectUrl + "?lockHandle=" + urlEncode(lockHandle);
        if (transport != null && !transport.isEmpty()) {
            deletePath = deletePath + "&corrNr=" + urlEncode(transport);
        }

        HttpResponse<String> response = client.deleteWithHeaders(deletePath, STATEFUL_HEADERS);

        JsonObject output = new JsonObject();
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            output.addProperty("status", "success");
            output.addProperty("message", "Object deleted successfully.");
        } else {
            output.addProperty("status", "error");
            output.addProperty("errorBody", response.body());
        }
        output.addProperty("statusCode", response.statusCode());

        return output.toString();
    }
}
