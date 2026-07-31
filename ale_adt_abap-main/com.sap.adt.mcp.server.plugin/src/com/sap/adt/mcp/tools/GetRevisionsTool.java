package com.sap.adt.mcp.tools;

import java.net.http.HttpResponse;

import com.google.gson.JsonObject;
import com.sap.adt.mcp.sap.AdtRestClient;
import com.sap.adt.mcp.sap.AdtXmlParser;

/**
 * Tool: sap_get_revisions -- Read the version/revision history of an ABAP object
 * (who changed it and when), useful for audit and Clean Core impact analysis.
 *
 * Ported from the abap-adt-api reference client (src/api/revisions.ts): the object's
 * structure exposes a link with rel="http://www.sap.com/adt/relations/versions" --
 * this tool fetches the object structure first to find that link, then GETs it as an
 * Atom feed of revisions.
 */
public class GetRevisionsTool extends AbstractMcpTool {

    public static final String NAME = "sap_get_revisions";

    public GetRevisionsTool(AdtRestClient client) {
        super(client);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return "Read the version/revision history of an ABAP object (who changed it and when). "
                + "Returns an empty list if the object has no exposed version history link.";
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
        String objectUrl = resolveObjectUrlArg(arguments, "objectUrl");
        if (objectUrl == null) {
            throw new IllegalArgumentException("Provide objectType + objectName (or objectUrl).");
        }

        HttpResponse<String> structureResp = client.get(objectUrl, "application/*");
        JsonObject structure = AdtXmlParser.parseObjectStructure(structureResp.body());
        String versionsLink = AdtXmlParser.findVersionsLink(structure);

        JsonObject output = new JsonObject();
        if (versionsLink == null || versionsLink.isEmpty()) {
            output.addProperty("found", false);
            output.addProperty("note", "This object has no exposed version history link "
                    + "(http://www.sap.com/adt/relations/versions) on this backend.");
            return output.toString();
        }

        // O href pode vir relativo ao objeto (ex.: "includes/main/versions"); resolve para
        // a URL absoluta concatenando com a base do objeto, senão o GET cai em 404.
        if (!versionsLink.startsWith("/") && !versionsLink.startsWith("http")) {
            String base = objectUrl;
            int q = base.indexOf('?');
            if (q >= 0) {
                base = base.substring(0, q);
            }
            if (base.endsWith("/")) {
                base = base.substring(0, base.length() - 1);
            }
            versionsLink = base + "/" + versionsLink;
        }

        HttpResponse<String> revisionsResp = client.get(versionsLink, "application/atom+xml;type=feed");
        output.addProperty("found", true);
        output.add("revisions", AdtXmlParser.parseAtomFeed(revisionsResp.body()));
        return output.toString();
    }
}
