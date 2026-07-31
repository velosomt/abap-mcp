package com.sap.adt.mcp.tools;

import java.net.http.HttpResponse;

import com.google.gson.JsonObject;
import com.sap.adt.mcp.sap.AdtRestClient;
import com.sap.adt.mcp.sap.AdtXmlParser;

/**
 * Shared lock -> write -> activate -> unlock flow for writing ABAP source code,
 * extracted from SetSourceTool so other tools (e.g. CreateObjectTool's initialSource) can reuse it.
 */
public final class AdtSourceWriter {

    private AdtSourceWriter() {}

    public static String lockWriteUnlock(AdtRestClient client, String objectSourceUrl, String source, String transport) throws Exception {
        final int maxAttempts = 3;
        Exception lastError = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return lockWriteUnlockAttempt(client, objectSourceUrl, source, transport);
            } catch (java.io.IOException e) {
                lastError = e;
                String msg = e.getMessage() != null ? e.getMessage() : "";
                if (msg.contains("HTTP 423") && attempt < maxAttempts) {
                    Thread.sleep(500);
                    continue;
                }
                throw e;
            }
        }
        throw lastError;
    }

    private static String lockWriteUnlockAttempt(AdtRestClient client, String sourceUrl, String source, String transport) throws Exception {
        String lockUrl = AbstractMcpTool.toLockUrl(sourceUrl);

        // Lock
        String lockPath = lockUrl + "?_action=LOCK&accessMode=MODIFY";
        HttpResponse<String> lockResp = client.postWithHeaders(lockPath, "",
                "application/*",
                "application/vnd.sap.as+xml;charset=UTF-8;dataname=com.sap.adt.lock.result;q=0.8, "
                + "application/vnd.sap.as+xml;charset=UTF-8;dataname=com.sap.adt.lock.result2;q=0.9",
                AbstractMcpTool.STATEFUL_HEADERS);
        String lockHandle = AdtXmlParser.extractLockHandle(lockResp.body());

        if (lockHandle == null || lockHandle.isEmpty()) {
            throw new RuntimeException("Failed to acquire lock on " + lockUrl + ". Response: " + lockResp.body());
        }

        try {
            // Write
            String writePath = sourceUrl + "?lockHandle=" + AbstractMcpTool.urlEncode(lockHandle);
            if (transport != null && !transport.isEmpty()) {
                writePath = writePath + "&corrNr=" + AbstractMcpTool.urlEncode(transport);
            }

            HttpResponse<String> response = client.putWithHeaders(writePath, source,
                    "text/plain; charset=utf-8", AbstractMcpTool.STATEFUL_HEADERS);

            JsonObject output = new JsonObject();
            output.addProperty("status", "success");
            output.addProperty("statusCode", response.statusCode());

            // Activate
            String objectUrl = sourceUrl;
            if (objectUrl.endsWith("/source/main")) {
                objectUrl = objectUrl.substring(0, objectUrl.length() - "/source/main".length());
            }
            String objectName = extractObjectName(objectUrl);
            try {
                String activateXml = "<adtcore:objectReferences xmlns:adtcore=\"http://www.sap.com/adt/core\">"
                        + "<adtcore:objectReference adtcore:uri=\"" + AbstractMcpTool.escapeXml(objectUrl)
                        + "\" adtcore:name=\"" + AbstractMcpTool.escapeXml(objectName) + "\"/>"
                        + "</adtcore:objectReferences>";

                client.post(
                        "/sap/bc/adt/activation?method=activate&preauditRequested=true",
                        activateXml,
                        "application/xml",
                        "application/xml,application/vnd.sap.adt.inactivectsobjects.v1+xml;q=0.9");

                output.addProperty("activated", true);
            } catch (Exception e) {
                output.addProperty("activated", false);
                output.addProperty("activationError", e.getMessage());
            }

            return output.toString();
        } finally {
            safeUnlock(client, lockUrl, lockHandle);
        }
    }

    private static String extractObjectName(String objectUrl) {
        if (objectUrl == null) return "UNKNOWN";
        String[] parts = objectUrl.split("/");
        for (int i = parts.length - 1; i >= 0; i--) {
            if (!parts[i].isEmpty()) {
                return parts[i].toUpperCase();
            }
        }
        return "UNKNOWN";
    }

    private static void safeUnlock(AdtRestClient client, String lockUrl, String lockHandle) {
        try {
            String unlockPath = lockUrl + "?_action=UNLOCK&lockHandle=" + AbstractMcpTool.urlEncode(lockHandle);
            client.postWithHeaders(unlockPath, "", "application/*", "application/*", AbstractMcpTool.STATEFUL_HEADERS);
        } catch (Exception e) {
            // Ignore
        }
    }
}
