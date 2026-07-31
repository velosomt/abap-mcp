package com.sap.adt.mcp.tools;

import java.net.http.HttpResponse;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sap.adt.mcp.sap.AdtRestClient;
import com.sap.adt.mcp.sap.AdtXmlParser;

/**
 * Tool: sap_atc_quickfix_evaluate -- Ask SAP which quickfixes are available
 * for a given ATC finding. The markerId comes from the "quickfixInfo" field
 * of a finding returned by sap_atc_run. Real ADT endpoint
 * (/sap/bc/adt/quickfixes/evaluation) -- returns an empty list if SAP has
 * nothing to offer, never a fabricated fix.
 */
public class AtcQuickfixEvaluateTool extends AbstractMcpTool {

    public static final String NAME = "sap_atc_quickfix_evaluate";

    public AtcQuickfixEvaluateTool(AdtRestClient client) {
        super(client);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return "List the quickfixes SAP can offer for a specific ATC finding. "
                + "Use the 'quickfixInfo' value of a finding from sap_atc_run as markerId. "
                + "Follow up with sap_atc_quickfix_apply to get the actual proposed source change.";
    }

    @Override
    public JsonObject getInputSchema() {
        JsonObject properties = new JsonObject();
        properties.add("objectType", AdtUrlResolver.buildTypeProperty());
        properties.add("objectName", AdtUrlResolver.buildNameProperty());

        JsonObject markerProp = new JsonObject();
        markerProp.addProperty("type", "string");
        markerProp.addProperty("description", "Marker id for the finding -- the 'quickfixInfo' field returned by sap_atc_run.");
        properties.add("markerId", markerProp);

        JsonArray required = new JsonArray();
        required.add("objectType");
        required.add("objectName");
        required.add("markerId");

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

        String markerId = optString(arguments, "markerId");
        if (markerId == null || markerId.isEmpty()) {
            throw new IllegalArgumentException("markerId is required (quickfixInfo from an sap_atc_run finding).");
        }

        String path = "/sap/bc/adt/quickfixes/evaluation"
                + "?uri=" + urlEncode(objectUrl)
                + "&markerId=" + urlEncode(markerId)
                + "&markerIdIsFilter=true";

        String evaluationXml = "<?xml version=\"1.0\" encoding=\"ASCII\"?>"
                + "<quickfixes:evaluationRequest xmlns:quickfixes=\"http://www.sap.com/adt/quickfixes\"/>";

        HttpResponse<String> response = client.post(
                path, evaluationXml,
                "application/vnd.sap.adt.quickfixes.evaluation+xml;version=1.0.0",
                "application/xml");

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Quickfix evaluation failed: HTTP " + response.statusCode()
                    + " - " + response.body());
        }

        JsonObject result = new JsonObject();
        result.add("quickfixes", AdtXmlParser.parseQuickfixEvaluations(response.body()));
        return result.toString();
    }
}
