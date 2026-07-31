package com.sap.adt.mcp.tools;

import java.net.URI;
import java.net.http.HttpResponse;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.sap.adt.mcp.sap.AdtRestClient;
import com.sap.adt.mcp.sap.AdtXmlParser;

/**
 * Tool: sap_activate_batch -- Activate multiple ABAP objects together in one SAP-side
 * run, via /sap/bc/adt/activation/runs (preauditRequested=false). Unlike sap_activate
 * (strictly one object per call), this is what resolves circular dependencies between
 * objects that reference each other -- e.g. a CDS interface view and its projection
 * view -- which sequential single-object activation cannot activate either one first.
 *
 * Ported from the AWS SAP ABAP Accelerator MCP (activate_objects_batch), which documents
 * this exact endpoint/polling sequence as the fix for circular-dependency activation.
 */
public class ActivateBatchTool extends AbstractMcpTool {

    public static final String NAME = "sap_activate_batch";

    public ActivateBatchTool(AdtRestClient client) {
        super(client);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return "Activate multiple ABAP objects together in a single SAP-side run. Use this instead of "
                + "repeated sap_activate calls when objects reference each other (circular dependency), e.g. "
                + "mutually-referencing CDS interface/projection views, where activating either one alone fails.";
    }

    @Override
    public JsonObject getInputSchema() {
        JsonObject itemProperties = new JsonObject();
        itemProperties.add("objectType", AdtUrlResolver.buildTypeProperty());
        itemProperties.add("objectName", AdtUrlResolver.buildNameProperty());

        JsonArray itemRequired = new JsonArray();
        itemRequired.add("objectType");
        itemRequired.add("objectName");

        JsonObject itemSchema = new JsonObject();
        itemSchema.addProperty("type", "object");
        itemSchema.add("properties", itemProperties);
        itemSchema.add("required", itemRequired);

        JsonObject objectsProp = new JsonObject();
        objectsProp.addProperty("type", "array");
        objectsProp.addProperty("description",
                "Objects to activate together, e.g. [{\"objectType\":\"DDLS/DF\",\"objectName\":\"ZI_A\"},"
                + "{\"objectType\":\"DDLS/DF\",\"objectName\":\"ZC_A\"}]. At least 2 objects is the typical "
                + "use case (circular dependency); a single object also works but sap_activate is simpler for that.");
        objectsProp.add("items", itemSchema);

        JsonObject maxWaitProp = new JsonObject();
        maxWaitProp.addProperty("type", "number");
        maxWaitProp.addProperty("description", "Maximum seconds to wait for the activation run to finish before giving up (default: 60).");

        JsonObject pollIntervalProp = new JsonObject();
        pollIntervalProp.addProperty("type", "number");
        pollIntervalProp.addProperty("description", "Seconds to wait between status polls (default: 2).");

        JsonObject properties = new JsonObject();
        properties.add("objects", objectsProp);
        properties.add("maxWaitTime", maxWaitProp);
        properties.add("pollInterval", pollIntervalProp);

        JsonArray required = new JsonArray();
        required.add("objects");

        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.add("properties", properties);
        schema.add("required", required);

        return schema;
    }

    @Override
    public String execute(JsonObject arguments) throws Exception {
        if (!arguments.has("objects") || !arguments.get("objects").isJsonArray()) {
            throw new IllegalArgumentException("Provide 'objects': a list of {objectType, objectName}.");
        }
        JsonArray objectsArg = arguments.getAsJsonArray("objects");
        if (objectsArg.size() == 0) {
            throw new IllegalArgumentException("'objects' must contain at least one {objectType, objectName} entry.");
        }

        long maxWaitTimeMs = hasNumber(arguments, "maxWaitTime")
                ? (long) (arguments.get("maxWaitTime").getAsDouble() * 1000) : 60000L;
        long pollIntervalMs = hasNumber(arguments, "pollInterval")
                ? (long) (arguments.get("pollInterval").getAsDouble() * 1000) : 2000L;

        StringBuilder refsXml = new StringBuilder();
        JsonArray namesArr = new JsonArray();
        for (JsonElement el : objectsArg) {
            JsonObject entry = el.getAsJsonObject();
            String objectUrl = resolveObjectUrlArg(entry, "objectUrl");
            if (objectUrl == null) {
                throw new IllegalArgumentException("Each entry in 'objects' needs objectType + objectName.");
            }
            String objectName = entry.has("objectName")
                    ? entry.get("objectName").getAsString().toUpperCase() : "UNKNOWN";
            namesArr.add(objectName);
            refsXml.append("<adtcore:objectReference adtcore:uri=\"").append(escapeXml(objectUrl))
                    .append("\" adtcore:name=\"").append(escapeXml(objectName)).append("\"/>");
        }

        String activateXml = "<adtcore:objectReferences xmlns:adtcore=\"http://www.sap.com/adt/core\">"
                + refsXml + "</adtcore:objectReferences>";

        HttpResponse<String> response = client.post(
                "/sap/bc/adt/activation/runs?method=activate&preauditRequested=false",
                activateXml,
                "application/xml",
                "application/xml,application/vnd.sap.adt.inactivectsobjects.v1+xml;q=0.9");

        String resultBody = response.body();

        if (response.statusCode() == 201) {
            String location = toRelativePath(response.headers().firstValue("Location").orElse(null));
            if (location == null || location.isEmpty()) {
                throw new IllegalStateException(
                        "Batch activation accepted (201) but SAP returned no Location header to poll results from.");
            }

            long deadline = System.currentTimeMillis() + maxWaitTimeMs;
            boolean finished = false;
            while (System.currentTimeMillis() < deadline) {
                String separator = location.contains("?") ? "&" : "?";
                HttpResponse<String> pollResponse = client.get(
                        location + separator + "withLongPolling=true",
                        "application/xml,application/vnd.sap.adt.backgroundrun.v1+xml");
                resultBody = pollResponse.body();
                String status = AdtXmlParser.parseAtcRunStatus(resultBody);
                if ("finished".equalsIgnoreCase(status)) {
                    finished = true;
                    break;
                }
                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0) break;
                try {
                    Thread.sleep(Math.min(pollIntervalMs, remaining));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted while waiting for batch activation to finish.", e);
                }
            }

            if (!finished) {
                throw new IllegalStateException("Batch activation timed out after " + (maxWaitTimeMs / 1000)
                        + "s -- it may still be processing on the SAP side. Increase maxWaitTime and retry, "
                        + "or check sap_inactive_objects.");
            }

            String resultLink = toRelativePath(AdtXmlParser.extractResultLink(resultBody));
            if (resultLink != null && !resultLink.isEmpty()) {
                HttpResponse<String> resultResponse = client.get(resultLink, "application/xml");
                resultBody = resultResponse.body();
            }
            // If SAP didn't expose a separate result link, the "finished" status payload
            // itself may already carry the per-object messages -- parse it as-is below.
        }

        JsonObject result = AdtXmlParser.parseBatchActivationResult(resultBody);
        JsonObject output = new JsonObject();
        output.addProperty("success", result.get("success").getAsBoolean());
        output.add("messages", result.get("messages"));
        output.add("objects", namesArr);
        return output.toString();
    }

    private boolean hasNumber(JsonObject obj, String key) {
        return obj.has(key) && !obj.get(key).isJsonNull();
    }

    private String toRelativePath(String location) {
        if (location == null || location.isEmpty()) return location;
        if (location.startsWith("http://") || location.startsWith("https://")) {
            try {
                URI uri = URI.create(location);
                String path = uri.getRawPath();
                String query = uri.getRawQuery();
                return query != null ? path + "?" + query : path;
            } catch (Exception e) {
                return location;
            }
        }
        return location;
    }
}
