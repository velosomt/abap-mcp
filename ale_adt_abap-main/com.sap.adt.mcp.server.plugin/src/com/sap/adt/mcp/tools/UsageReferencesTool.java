package com.sap.adt.mcp.tools;

import java.net.http.HttpResponse;

import com.google.gson.JsonObject;
import com.sap.adt.mcp.sap.AdtRestClient;

/**
 * Tool: sap_usage_references -- Find where-used list.
 */
public class UsageReferencesTool extends AbstractMcpTool {

    public static final String NAME = "sap_usage_references";

    public UsageReferencesTool(AdtRestClient client) {
        super(client);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return "Find all usages (where-used) of an ABAP element across the system.";
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
        String url = resolveSourceUrlArg(arguments, "url");
        if (url == null) {
            throw new IllegalArgumentException("Provide objectType + objectName.");
        }
        // O endpoint de where-used espera a URL de source com uma posição (#start) e um
        // corpo XML de requisição com o content-type versionado. Sem isso o backend
        // responde HTTP 404 "No suitable resource found".
        url = ensureSourceUrl(url);
        String uriWithPos = url + "#start=1,0";

        String body = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<usagereferences:usageReferenceRequest xmlns:usagereferences=\"http://www.sap.com/adt/ris/usageReferences\">"
                + "<usagereferences:affectedObjects/>"
                + "</usagereferences:usageReferenceRequest>";

        String path = "/sap/bc/adt/repository/informationsystem/usageReferences?uri=" + urlEncode(uriWithPos);

        HttpResponse<String> response = client.post(path, body,
                "application/vnd.sap.adt.repository.usagereferences.request.v1+xml",
                "application/vnd.sap.adt.repository.usagereferences.result.v1+xml");

        JsonObject output = new JsonObject();
        output.addProperty("statusCode", response.statusCode());
        output.addProperty("response", response.body());
        return output.toString();
    }
}
