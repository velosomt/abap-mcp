package com.sap.adt.mcp.tools;

import java.net.http.HttpResponse;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sap.adt.mcp.sap.AdtRestClient;
import com.sap.adt.mcp.sap.AdtXmlParser;

/**
 * Tool: sap_atc_quickfix_apply -- Get the actual proposed source change for a
 * quickfix returned by sap_atc_quickfix_evaluate. Real ADT endpoint
 * (/sap/bc/adt/quickfixes/proposals/providers/atc/quickfixes/{id}).
 *
 * This only RETURNS the proposed source -- it never writes it back to SAP.
 * Review the diff against the current source and call sap_set_source
 * yourself if you want to apply it.
 */
public class AtcQuickfixApplyTool extends AbstractMcpTool {

    public static final String NAME = "sap_atc_quickfix_apply";

    public AtcQuickfixApplyTool(AdtRestClient client) {
        super(client);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return "Get the proposed source change for a quickfix (from sap_atc_quickfix_evaluate). "
                + "Returns the proposed source as a proposal only -- does NOT write it back to SAP. "
                + "Review it and call sap_set_source yourself to actually apply it.";
    }

    @Override
    public JsonObject getInputSchema() {
        JsonObject properties = new JsonObject();
        properties.add("objectType", AdtUrlResolver.buildTypeProperty());
        properties.add("objectName", AdtUrlResolver.buildNameProperty());

        JsonObject quickfixIdProp = new JsonObject();
        quickfixIdProp.addProperty("type", "string");
        quickfixIdProp.addProperty("description", "quickfixId returned by sap_atc_quickfix_evaluate.");
        properties.add("quickfixId", quickfixIdProp);

        JsonObject markerProp = new JsonObject();
        markerProp.addProperty("type", "string");
        markerProp.addProperty("description", "Same markerId used in sap_atc_quickfix_evaluate (quickfixInfo from the finding).");
        properties.add("markerId", markerProp);

        JsonObject sourceProp = new JsonObject();
        sourceProp.addProperty("type", "string");
        sourceProp.addProperty("description", "Current source of the object (e.g. from sap_get_source) that the quickfix will be evaluated against.");
        properties.add("sourceCode", sourceProp);

        JsonArray required = new JsonArray();
        required.add("objectType");
        required.add("objectName");
        required.add("quickfixId");
        required.add("markerId");
        required.add("sourceCode");

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

        String quickfixId = optString(arguments, "quickfixId");
        String markerId = optString(arguments, "markerId");
        String sourceCode = optString(arguments, "sourceCode");
        if (quickfixId == null || quickfixId.isEmpty()) {
            throw new IllegalArgumentException("quickfixId is required.");
        }
        if (markerId == null || markerId.isEmpty()) {
            throw new IllegalArgumentException("markerId is required.");
        }
        if (sourceCode == null) {
            throw new IllegalArgumentException("sourceCode is required.");
        }

        String[] markerParts = markerId.split(",", 2);
        String itemId = markerParts[0];
        String checkRunIndex = markerParts.length > 1 ? markerParts[1] : "212";

        String proposalXml = "<?xml version=\"1.0\" encoding=\"ASCII\"?>"
                + "<quickfixes:proposalRequest xmlns:adtcore=\"http://www.sap.com/adt/core\" "
                + "xmlns:quickfixes=\"http://www.sap.com/adt/quickfixes\">"
                + "<input>"
                + "<content>" + escapeXml(sourceCode) + "</content>"
                + "<adtcore:objectReference adtcore:uri=\"" + escapeXml(objectUrl) + "\"/>"
                + "</input>"
                + "<userContent>ITEMID=" + escapeXml(itemId) + "; CHECK_RUN_INDEX=" + escapeXml(checkRunIndex) + "</userContent>"
                + "</quickfixes:proposalRequest>";

        String path = "/sap/bc/adt/quickfixes/proposals/providers/atc/quickfixes/" + urlEncode(quickfixId);

        HttpResponse<String> response = client.post(path, proposalXml, "application/xml", "application/xml");

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Quickfix proposal failed: HTTP " + response.statusCode()
                    + " - " + response.body());
        }

        JsonObject result = AdtXmlParser.parseQuickfixProposal(response.body());
        result.addProperty("quickfixId", quickfixId);
        return result.toString();
    }
}
