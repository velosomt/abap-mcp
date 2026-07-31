package com.sap.adt.mcp.tools;

import java.net.http.HttpResponse;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sap.adt.mcp.sap.AdtRestClient;
import com.sap.adt.mcp.sap.AdtXmlParser;

/**
 * Tool: sap_atc_run -- Run ATC quality checks.
 *
 * SAP's ATC run is asynchronous: starting a run only schedules it, and the
 * worklist stays empty until the run actually finishes. This tool starts the
 * run with clientWait=false, polls the dedicated run-status endpoint until it
 * reports "finished" (or "failed"), and only then fetches the worklist.
 */
public class AtcRunTool extends AbstractMcpTool {

    public static final String NAME = "sap_atc_run";

    public AtcRunTool(AdtRestClient client) {
        super(client);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return "Run ATC quality checks (waits for the asynchronous run to finish before returning). Returns findings with priority and messages.";
    }

    @Override
    public JsonObject getInputSchema() {
        JsonObject properties = new JsonObject();
        properties.add("objectType", AdtUrlResolver.buildTypeProperty());
        properties.add("objectName", AdtUrlResolver.buildNameProperty());

        JsonObject variantProp = new JsonObject();
        variantProp.addProperty("type", "string");
        variantProp.addProperty("description", "ATC check variant to use (default: DEFAULT).");
        properties.add("variant", variantProp);

        JsonObject maxWaitProp = new JsonObject();
        maxWaitProp.addProperty("type", "number");
        maxWaitProp.addProperty("description", "Maximum seconds to wait for the ATC run to finish before giving up (default: 60).");
        properties.add("maxWaitTime", maxWaitProp);

        JsonObject pollIntervalProp = new JsonObject();
        pollIntervalProp.addProperty("type", "number");
        pollIntervalProp.addProperty("description", "Seconds to wait between status polls (default: 2).");
        properties.add("pollInterval", pollIntervalProp);

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

        String variant = optString(arguments, "variant");
        if (variant == null || variant.isEmpty()) variant = "DEFAULT";

        long maxWaitTimeMs = hasNumber(arguments, "maxWaitTime")
                ? (long) (arguments.get("maxWaitTime").getAsDouble() * 1000) : 60000L;
        long pollIntervalMs = hasNumber(arguments, "pollInterval")
                ? (long) (arguments.get("pollInterval").getAsDouble() * 1000) : 2000L;

        int maxResults = 100;

        // Step 1: create the worklist (this id is what we fetch results from at the end).
        HttpResponse<String> wlResponse = client.post(
                "/sap/bc/adt/atc/worklists?checkVariant=" + urlEncode(variant),
                "", "application/xml", "text/plain");
        requireSuccess(wlResponse, "Failed to create ATC worklist");
        String worklistId = wlResponse.body() != null ? wlResponse.body().trim() : "";
        if (worklistId.isEmpty()) {
            throw new IllegalStateException("Failed to create ATC worklist: empty worklistId returned by SAP.");
        }

        // Step 2: start the run asynchronously (clientWait=false is what makes SAP return
        // immediately with a run id instead of blocking until the check finishes).
        String runXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<atc:run maximumVerdicts=\"" + maxResults + "\" xmlns:atc=\"http://www.sap.com/adt/atc\">"
                + "<objectSets xmlns:adtcore=\"http://www.sap.com/adt/core\">"
                + "<objectSet kind=\"inclusive\">"
                + "<adtcore:objectReferences>"
                + "<adtcore:objectReference adtcore:uri=\"" + escapeXml(objectUrl) + "\"/>"
                + "</adtcore:objectReferences>"
                + "</objectSet>"
                + "</objectSets>"
                + "</atc:run>";

        HttpResponse<String> runResponse = client.post(
                "/sap/bc/adt/atc/runs?worklistId=" + urlEncode(worklistId) + "&clientWait=false",
                runXml, "application/xml", "application/xml");
        requireSuccess(runResponse, "Failed to start ATC run");

        // The Location header here points to the run (status) resource, NOT the worklist.
        // Treating it as the worklistId (as the old code did) made the final GET below
        // fetch the wrong resource and always come back empty.
        String runId = extractIdFromLocation(runResponse);

        // Step 3: poll the run until SAP reports it finished -- this is the step that was
        // entirely missing before, and the actual root cause of totalFindings always being 0.
        if (runId != null && !runId.isEmpty()) {
            long deadline = System.currentTimeMillis() + maxWaitTimeMs;
            String status = "running";
            while (System.currentTimeMillis() < deadline) {
                HttpResponse<String> statusResponse = client.get(
                        "/sap/bc/adt/atc/runs/" + urlEncode(runId),
                        "application/vnd.sap.adt.backgroundrun.v1+xml");
                requireSuccess(statusResponse, "Failed to poll ATC run " + runId + " status");
                status = AdtXmlParser.parseAtcRunStatus(statusResponse.body());
                if ("finished".equalsIgnoreCase(status) || "failed".equalsIgnoreCase(status)) {
                    break;
                }
                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0) break;
                try {
                    Thread.sleep(Math.min(pollIntervalMs, remaining));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted while waiting for ATC run " + runId + " to finish.", e);
                }
            }
            if ("failed".equalsIgnoreCase(status)) {
                throw new IllegalStateException("ATC run " + runId + " failed on the SAP side.");
            }
        }
        // If no run id came back (older/synchronous backend behavior), fall through --
        // the worklist may already be populated.

        // Step 4: fetch the worklist results, always keyed by the original worklistId.
        HttpResponse<String> worklistResponse = client.get(
                "/sap/bc/adt/atc/worklists/" + urlEncode(worklistId)
                        + "?includeExemptedFindings=false&usedObjectSet=99999999999999999999999999999999",
                "application/atc.worklist.v1+xml");
        requireSuccess(worklistResponse, "Failed to fetch ATC worklist " + worklistId);

        JsonObject worklist = AdtXmlParser.parseAtcWorklist(worklistResponse.body());
        worklist.addProperty("worklistId", worklistId);
        return worklist.toString();
    }

    private boolean hasNumber(JsonObject obj, String key) {
        return obj.has(key) && !obj.get(key).isJsonNull();
    }

    private void requireSuccess(HttpResponse<String> response, String message) {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException(message + ": HTTP " + response.statusCode() + " - " + response.body());
        }
    }

    private String extractIdFromLocation(HttpResponse<String> response) {
        String location = response.headers().firstValue("Location").orElse(null);
        if (location != null && !location.isEmpty()) {
            int lastSlash = location.lastIndexOf('/');
            if (lastSlash >= 0 && lastSlash < location.length() - 1) {
                String id = location.substring(lastSlash + 1);
                int qMark = id.indexOf('?');
                if (qMark >= 0) id = id.substring(0, qMark);
                return id;
            }
        }
        return null;
    }
}
