package com.sap.adt.mcp.tools;

import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sap.adt.mcp.sap.AdtRestClient;
import com.sap.adt.mcp.sap.AdtXmlParser;

/**
 * Tool: sap_syntax_check_source -- Run ABAP syntax check on a draft 'content' string,
 * WITHOUT first writing/activating it on the object. Uses the same /checkruns endpoint as
 * sap_syntax_check, but sends the draft source inline as a base64 chkrun:artifact instead of
 * checking whatever is already persisted -- this is the same payload shape the ADT/Eclipse
 * editor itself sends for live (unsaved) syntax checking.
 */
public class SyntaxCheckSourceTool extends AbstractMcpTool {

    public static final String NAME = "sap_syntax_check_source";

    public SyntaxCheckSourceTool(AdtRestClient client) {
        super(client);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return "Verifica a sintaxe de um conteudo ABAP passado em 'content' (rascunho), SEM gravar ou ativar o "
            + "objeto antes. Util para validar uma alteracao antes de chamar sap_set_source. O objeto referenciado "
            + "por objectType+objectName precisa ja existir (mesmo que com outro conteudo) -- apenas o texto "
            + "verificado e' o rascunho informado, nao o que esta persistido. Diferente de sap_syntax_check, que "
            + "sempre verifica o conteudo ja salvo no objeto.";
    }

    @Override
    public JsonObject getInputSchema() {
        JsonObject properties = new JsonObject();
        properties.add("objectType", AdtUrlResolver.buildTypeProperty());
        properties.add("objectName", AdtUrlResolver.buildNameProperty());

        JsonObject contentProp = new JsonObject();
        contentProp.addProperty("type", "string");
        contentProp.addProperty("description", "Conteudo ABAP completo do rascunho a verificar (ainda nao gravado no objeto).");
        properties.add("content", contentProp);

        JsonObject versionProp = new JsonObject();
        versionProp.addProperty("type", "string");
        versionProp.addProperty("description", "Versao do objeto contra a qual verificar: 'active' ou 'inactive'. Default: 'active'.");
        properties.add("version", versionProp);

        JsonArray required = new JsonArray();
        required.add("objectType");
        required.add("objectName");
        required.add("content");

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
        String sourceUrl = ensureSourceUrl(resolveSourceUrlArg(arguments, "objectSourceUrl"));

        String content = optString(arguments, "content");
        if (content == null) {
            throw new IllegalArgumentException("Provide 'content' (the draft ABAP source to check).");
        }

        String version = optString(arguments, "version");
        if (version == null || version.isEmpty()) version = "active";

        String encodedContent = Base64.getEncoder().encodeToString(content.getBytes(StandardCharsets.UTF_8));

        String checkXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<chkrun:checkObjectList xmlns:chkrun=\"http://www.sap.com/adt/checkrun\" "
            + "xmlns:adtcore=\"http://www.sap.com/adt/core\">\n"
            + "  <chkrun:checkObject adtcore:uri=\"" + escapeXml(objectUrl) + "\" chkrun:version=\"" + escapeXml(version) + "\">\n"
            + "    <chkrun:artifacts>\n"
            + "      <chkrun:artifact chkrun:contentType=\"text/plain; charset=utf-8\" chkrun:uri=\"" + escapeXml(sourceUrl) + "\">\n"
            + "        <chkrun:content>" + encodedContent + "</chkrun:content>\n"
            + "      </chkrun:artifact>\n"
            + "    </chkrun:artifacts>\n"
            + "  </chkrun:checkObject>\n"
            + "</chkrun:checkObjectList>";

        HttpResponse<String> response = client.post(
            "/sap/bc/adt/checkruns?reporters=abapCheckRun",
            checkXml,
            "application/vnd.sap.adt.checkobjects+xml",
            "application/vnd.sap.adt.checkmessages+xml");

        JsonArray results = AdtXmlParser.parseSyntaxCheckResults(response.body());

        int errorCount = 0;
        int warningCount = 0;
        for (int i = 0; i < results.size(); i++) {
            JsonObject item = results.get(i).getAsJsonObject();
            String severity = item.has("severity") ? item.get("severity").getAsString() : "";
            if ("error".equalsIgnoreCase(severity)) {
                errorCount++;
            } else if ("warning".equalsIgnoreCase(severity)) {
                warningCount++;
            }
        }

        JsonObject output = new JsonObject();
        output.addProperty("errors", errorCount);
        output.addProperty("warnings", warningCount);
        output.addProperty("success", errorCount == 0);
        output.add("messages", results);

        return output.toString();
    }
}
