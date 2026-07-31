package com.sap.adt.mcp.tools;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sap.adt.mcp.sap.AdtRestClient;

/**
 * Tool: sap_create_and_validate -- Orchestrator that chains the "create an object
 * safely" sequence developers do by hand: sap_transport_check (skipped for $TMP) ->
 * sap_create_object -> sap_syntax_check (when initialSource was given) -> optionally
 * sap_atc_run. Every step delegates to the existing tool's own execute() against the
 * same client -- no new ADT endpoint, no fabricated transport/quickfix data.
 *
 * Accepts the exact same input as sap_create_object, plus three optional flags.
 */
public class CreateAndValidateTool extends AbstractMcpTool {

    public static final String NAME = "sap_create_and_validate";

    public CreateAndValidateTool(AdtRestClient client) {
        super(client);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return "Combo de criação: roda sap_transport_check (se o pacote não for $TMP e nenhum 'transport' "
                + "for passado) -> sap_create_object -> sap_syntax_check (quando há initialSource) -> "
                + "opcionalmente sap_atc_run. Mesmos parâmetros de sap_create_object, mais "
                + "runSyntaxCheck/runAtc/atcVariant.";
    }

    @Override
    public JsonObject getInputSchema() {
        JsonObject schema = new CreateObjectTool(client).getInputSchema();
        JsonObject properties = schema.getAsJsonObject("properties");

        JsonObject runSyntaxCheckProp = new JsonObject();
        runSyntaxCheckProp.addProperty("type", "boolean");
        runSyntaxCheckProp.addProperty("description",
                "Rodar sap_syntax_check após criar (só faz sentido se initialSource foi passado). Default: true.");
        properties.add("runSyntaxCheck", runSyntaxCheckProp);

        JsonObject runAtcProp = new JsonObject();
        runAtcProp.addProperty("type", "boolean");
        runAtcProp.addProperty("description", "Rodar sap_atc_run após criar. Default: false.");
        properties.add("runAtc", runAtcProp);

        JsonObject atcVariantProp = new JsonObject();
        atcVariantProp.addProperty("type", "string");
        atcVariantProp.addProperty("description", "Variant do sap_atc_run, se runAtc=true. Default: DEFAULT.");
        properties.add("atcVariant", atcVariantProp);

        return schema;
    }

    @Override
    public String execute(JsonObject arguments) throws Exception {
        String objtype = optString(arguments, "objtype");
        String name = optString(arguments, "name");
        String parentName = optString(arguments, "parentName");
        String transport = optString(arguments, "transport");
        String initialSource = optString(arguments, "initialSource");
        if (objtype == null || name == null || parentName == null) {
            throw new IllegalArgumentException("Provide objtype, name and parentName.");
        }

        JsonObject output = new JsonObject();

        boolean needsTransportCheck = !"$TMP".equalsIgnoreCase(parentName) && (transport == null || transport.isEmpty());
        if (needsTransportCheck) {
            JsonObject transportCheckArgs = new JsonObject();
            transportCheckArgs.addProperty("objectType", objtype);
            transportCheckArgs.addProperty("objectName", name);
            transportCheckArgs.addProperty("packageName", parentName);
            try {
                String transportCheckResult = new TransportCheckTool(client).execute(transportCheckArgs);
                output.add("transportCheck", JsonParser.parseString(transportCheckResult));
            } catch (Exception e) {
                output.addProperty("transportCheckError", e.getMessage());
            }
        }

        String createResult = new CreateObjectTool(client).execute(arguments);
        JsonObject createJson = JsonParser.parseString(createResult).getAsJsonObject();
        output.add("creation", createJson);

        boolean created = createJson.has("status") && "created".equals(createJson.get("status").getAsString());
        if (!created) {
            output.addProperty("note", "sap_create_object não retornou status=created; sap_syntax_check/sap_atc_run não foram executados.");
            return output.toString();
        }

        boolean runSyntaxCheck = !arguments.has("runSyntaxCheck") || arguments.get("runSyntaxCheck").getAsBoolean();
        if (runSyntaxCheck && initialSource != null && !initialSource.isEmpty()) {
            JsonObject syntaxArgs = new JsonObject();
            syntaxArgs.addProperty("objectType", objtype);
            syntaxArgs.addProperty("objectName", name);
            try {
                String syntaxResult = new SyntaxCheckTool(client).execute(syntaxArgs);
                output.add("syntaxCheck", JsonParser.parseString(syntaxResult));
            } catch (Exception e) {
                output.addProperty("syntaxCheckError", e.getMessage());
            }
        }

        boolean runAtc = arguments.has("runAtc") && arguments.get("runAtc").getAsBoolean();
        if (runAtc) {
            JsonObject atcArgs = new JsonObject();
            atcArgs.addProperty("objectType", objtype);
            atcArgs.addProperty("objectName", name);
            String atcVariant = optString(arguments, "atcVariant");
            if (atcVariant != null && !atcVariant.isEmpty()) {
                atcArgs.addProperty("variant", atcVariant);
            }
            try {
                String atcResult = new AtcRunTool(client).execute(atcArgs);
                output.add("atcRun", JsonParser.parseString(atcResult));
            } catch (Exception e) {
                output.addProperty("atcRunError", e.getMessage());
            }
        }

        return output.toString();
    }
}
