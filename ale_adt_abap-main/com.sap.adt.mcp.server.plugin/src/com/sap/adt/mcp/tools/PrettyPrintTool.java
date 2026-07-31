package com.sap.adt.mcp.tools;

import java.net.http.HttpResponse;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sap.adt.mcp.sap.AdtRestClient;

/**
 * Tool: sap_pretty_print -- Format ABAP source with the system Pretty Printer.
 * Strictly read-only: it returns the formatted text and writes nothing back.
 */
public class PrettyPrintTool extends AbstractMcpTool {

    public static final String NAME = "sap_pretty_print";

    public PrettyPrintTool(AdtRestClient client) {
        super(client);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return "Formats ABAP source code using the SAP system Pretty Printer and RETURNS the formatted text in "
            + "the 'formatted' field. Strictly read-only: it does NOT lock, save or activate anything. Pass the raw "
            + "ABAP source in 'source'. Typical use: tidy generated code before writing it with sap_set_source. "
            + "Endpoint: POST /sap/bc/adt/abapsource/prettyprinter.";
    }

    @Override
    public JsonObject getInputSchema() {
        JsonObject sourceProp = new JsonObject();
        sourceProp.addProperty("type", "string");
        sourceProp.addProperty("description", "The ABAP source code to format. Returned formatted; nothing is saved.");

        JsonObject properties = new JsonObject();
        properties.add("source", sourceProp);

        JsonArray required = new JsonArray();
        required.add("source");

        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.add("properties", properties);
        schema.add("required", required);
        return schema;
    }

    @Override
    public String execute(JsonObject arguments) throws Exception {
        String source = optString(arguments, "source");
        if (source == null || source.isEmpty()) {
            throw new IllegalArgumentException("Provide 'source' (the ABAP code to format).");
        }
        HttpResponse<String> response = client.post(
            "/sap/bc/adt/abapsource/prettyprinter", source, "text/plain", "text/plain");

        JsonObject output = new JsonObject();
        output.addProperty("statusCode", response.statusCode());
        output.addProperty("formatted", response.body());
        return output.toString();
    }
}
