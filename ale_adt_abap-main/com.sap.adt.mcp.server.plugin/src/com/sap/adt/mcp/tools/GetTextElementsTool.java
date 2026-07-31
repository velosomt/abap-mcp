package com.sap.adt.mcp.tools;

import java.net.http.HttpResponse;

import com.google.gson.JsonObject;
import com.sap.adt.mcp.sap.AdtRestClient;

/**
 * Tool: sap_get_text_elements -- Read the text elements (text symbols, selection texts,
 * list headers) of a PROG/CLAS/FUGR, which live in a separate ADT namespace from the
 * object's main ABAP source and are otherwise invisible to sap_get_source.
 *
 * Ported from the abap-adt-api reference client (src/api/textelements.ts): GET
 * /sap/bc/adt/textelements/{programs|classes|functiongroups}/{name}/source/{category}.
 * The response is a plain-text, line-based format (not XML) -- returned verbatim, same
 * as sap_get_source does for ABAP source.
 */
public class GetTextElementsTool extends AbstractMcpTool {

    public static final String NAME = "sap_get_text_elements";

    public GetTextElementsTool(AdtRestClient client) {
        super(client);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return "Read the text elements (text symbols, selection texts, list headers) of a PROG, CLAS or "
                + "FUGR. 'category' is the ADT text-element category, e.g. 'selections' for selection-screen "
                + "texts or 'header' for list headers; pass the exact category your backend exposes.";
    }

    @Override
    public JsonObject getInputSchema() {
        JsonObject objectTypeProp = new JsonObject();
        objectTypeProp.addProperty("type", "string");
        objectTypeProp.addProperty("description", "PROG/P, CLAS/OC or FUGR/F.");

        JsonObject categoryProp = new JsonObject();
        categoryProp.addProperty("type", "string");
        categoryProp.addProperty("description", "Text element category, e.g. 'selections' or 'header'.");

        JsonObject properties = new JsonObject();
        properties.add("objectType", objectTypeProp);
        properties.add("objectName", AdtUrlResolver.buildNameProperty());
        properties.add("category", categoryProp);

        com.google.gson.JsonArray required = new com.google.gson.JsonArray();
        required.add("objectType");
        required.add("objectName");
        required.add("category");

        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.add("properties", properties);
        schema.add("required", required);

        return schema;
    }

    static String textElementsBaseUrl(String objectType, String objectName) {
        if (objectType == null || objectName == null) return null;
        String name = objectName.toLowerCase();
        String type = objectType.toUpperCase();
        if (type.startsWith("PROG")) return "/sap/bc/adt/textelements/programs/" + name;
        if (type.startsWith("CLAS")) return "/sap/bc/adt/textelements/classes/" + name;
        if (type.startsWith("FUGR")) return "/sap/bc/adt/textelements/functiongroups/" + name;
        return null;
    }

    static String lockObjectUrl(String objectType, String objectName) {
        if (objectType == null || objectName == null) return null;
        String name = objectName.toLowerCase();
        String type = objectType.toUpperCase();
        if (type.startsWith("PROG")) return "/sap/bc/adt/programs/programs/" + name;
        if (type.startsWith("CLAS")) return "/sap/bc/adt/oo/classes/" + name;
        if (type.startsWith("FUGR")) return "/sap/bc/adt/functions/groups/" + name;
        return null;
    }

    /**
     * Category is interpolated unquoted into both the request path and the
     * Accept/Content-Type media-type header. Restrict it to a safe identifier shape so it
     * cannot inject header delimiters (CR/LF, ';', ',') or break out of the media-type value.
     */
    static boolean isValidCategory(String category) {
        return category != null && category.matches("[a-zA-Z0-9_-]+");
    }

    @Override
    public String execute(JsonObject arguments) throws Exception {
        String objectType = optString(arguments, "objectType");
        String objectName = optString(arguments, "objectName");
        String category = optString(arguments, "category");
        if (objectType == null || objectName == null || category == null) {
            throw new IllegalArgumentException("Provide objectType, objectName and category.");
        }
        if (!isValidCategory(category)) {
            throw new IllegalArgumentException("category must contain only letters, digits, '_' or '-'.");
        }

        String baseUrl = textElementsBaseUrl(objectType, objectName);
        if (baseUrl == null) {
            throw new IllegalArgumentException("objectType must be PROG/P, CLAS/OC or FUGR/F.");
        }

        HttpResponse<String> response = client.get(baseUrl + "/source/" + urlEncode(category),
                "application/vnd.sap.adt.textelements." + category + ".v1");
        return response.body();
    }
}
