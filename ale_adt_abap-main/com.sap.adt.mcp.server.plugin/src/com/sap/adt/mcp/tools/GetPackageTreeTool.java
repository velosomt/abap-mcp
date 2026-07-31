package com.sap.adt.mcp.tools;

import java.net.http.HttpResponse;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sap.adt.mcp.sap.AdtRestClient;
import com.sap.adt.mcp.sap.AdtXmlParser;

/**
 * Tool: sap_get_package_tree -- List the sub-packages and objects of an ABAP package,
 * the same tree SAP GUI/Eclipse's Project Explorer is built from. Complements
 * sap_search_object (which searches by name/pattern across the whole system) with direct
 * hierarchical browsing of one package.
 *
 * Ported from the abap-adt-api reference client (src/api/nodeContents.ts): POST
 * /sap/bc/adt/repository/nodestructure with parent_type/parent_name query parameters.
 */
public class GetPackageTreeTool extends AbstractMcpTool {

    public static final String NAME = "sap_get_package_tree";

    public GetPackageTreeTool(AdtRestClient client) {
        super(client);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return "List the sub-packages and objects directly inside an ABAP package (one level), "
                + "the same tree SAP GUI/Eclipse's Project Explorer shows.";
    }

    @Override
    public JsonObject getInputSchema() {
        JsonObject packageNameProp = new JsonObject();
        packageNameProp.addProperty("type", "string");
        packageNameProp.addProperty("description", "Package name (e.g. 'ZMY_PACKAGE' or '$TMP').");

        JsonObject properties = new JsonObject();
        properties.add("packageName", packageNameProp);

        JsonArray required = new JsonArray();
        required.add("packageName");

        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.add("properties", properties);
        schema.add("required", required);

        return schema;
    }

    @Override
    public String execute(JsonObject arguments) throws Exception {
        String packageName = optString(arguments, "packageName");
        if (packageName == null || packageName.isEmpty()) {
            throw new IllegalArgumentException("Provide packageName.");
        }

        String path = "/sap/bc/adt/repository/nodestructure"
                + "?parent_type=" + urlEncode("DEVC/K")
                + "&parent_name=" + urlEncode(packageName.toUpperCase())
                + "&withShortDescriptions=true";

        HttpResponse<String> response = client.postWithHeaders(path, "",
                "application/*", "application/*", STATEFUL_HEADERS);

        JsonObject output = new JsonObject();
        output.addProperty("packageName", packageName.toUpperCase());
        output.add("nodes", AdtXmlParser.parseNodeStructure(response.body()));
        return output.toString();
    }
}
