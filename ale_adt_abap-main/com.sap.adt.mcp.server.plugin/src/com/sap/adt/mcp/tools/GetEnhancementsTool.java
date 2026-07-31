package com.sap.adt.mcp.tools;

import java.net.http.HttpResponse;
import java.util.Set;

import com.google.gson.JsonObject;
import com.sap.adt.mcp.registry.AdtTypeAlias;
import com.sap.adt.mcp.sap.AdtRestClient;
import com.sap.adt.mcp.sap.AdtXmlParser;

/**
 * Tool: sap_get_enhancements -- List active enhancement implementations (BAdI/enhancement
 * spot/explicit enhancement points) on an ABAP object's source, e.g. for a Clean Core
 * readiness check (custom code that hooks via enhancements is a common migration risk).
 *
 * Ported from the abap-adt-api reference client (src/api/enhancements.ts): GET
 * {sourceUrl}/enhancements, falling back to {sourceUrl}/enhancements/elements on HTTP 404
 * (older ECC vs newer S/4HANA path variant).
 */
public class GetEnhancementsTool extends AbstractMcpTool {

    public static final String NAME = "sap_get_enhancements";

    /**
     * Enhancement points/BAdI implementations are an ABAP *source-code* concept (see SAP help
     * topic "ABAP Source Code Enhancements"): they hook into procedural statements. These types
     * are pure DDIC/text metadata with no procedural ABAP source to hook into, so the ADT
     * enhancements endpoint 404s for them on every backend (verified for TABL/DT against this
     * server) -- returning a clear "not applicable" result here instead of a raw 404 propagated
     * through two failed HTTP round-trips.
     */
    private static final Set<String> NO_ENHANCEMENTS_SUPPORT = Set.of(
            "TABL/DT", "TABL/DS", "DTEL/DE", "DOMA/DD", "MSAG/N");

    public GetEnhancementsTool(AdtRestClient client) {
        super(client);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return "List active enhancement implementations (BAdI/enhancement spot/explicit enhancement "
                + "points) on an ABAP object's source code.";
    }

    @Override
    public JsonObject getInputSchema() {
        JsonObject properties = new JsonObject();
        properties.add("objectType", AdtUrlResolver.buildTypeProperty());
        properties.add("objectName", AdtUrlResolver.buildNameProperty());

        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.add("properties", properties);

        return schema;
    }

    @Override
    public String execute(JsonObject arguments) throws Exception {
        String sourceUrl = resolveSourceUrlArg(arguments, "objectSourceUrl");
        if (sourceUrl == null) {
            throw new IllegalArgumentException("Provide objectType + objectName (or objectSourceUrl).");
        }

        String rawType = optString(arguments, "objectType");
        String normalizedType = rawType == null ? null : AdtTypeAlias.normalize(rawType);
        if (normalizedType != null && NO_ENHANCEMENTS_SUPPORT.contains(normalizedType)) {
            JsonObject output = new JsonObject();
            output.addProperty("status", "not_applicable");
            output.addProperty("reason", "Object type " + normalizedType + " is pure DDIC/text metadata "
                    + "(no procedural ABAP source), so it has no enhancement points/BAdI implementations to "
                    + "list. This check only applies to executable source objects (CLAS/OC, PROG/P, PROG/I, "
                    + "FUGR/F...).");
            output.add("enhancements", new com.google.gson.JsonArray());
            return output.toString();
        }

        HttpResponse<String> response;
        try {
            response = client.get(sourceUrl + "/enhancements", "application/*");
        } catch (Exception primaryError) {
            // Only fall back on the older-path 404; any other failure (auth, 500, network)
            // is the real error and should surface as-is instead of being masked by a
            // second, likely-also-failing request.
            if (primaryError.getMessage() != null && primaryError.getMessage().contains("404")) {
                response = client.get(sourceUrl + "/enhancements/elements", "application/*");
            } else {
                throw primaryError;
            }
        }

        JsonObject output = new JsonObject();
        output.add("enhancements", AdtXmlParser.parseEnhancements(response.body()));
        return output.toString();
    }
}
