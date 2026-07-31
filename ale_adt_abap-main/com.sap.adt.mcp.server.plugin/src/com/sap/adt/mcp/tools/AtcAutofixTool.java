package com.sap.adt.mcp.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sap.adt.mcp.sap.AdtRestClient;

/**
 * Tool: sap_atc_autofix -- Orchestrator for the "run ATC, then fix what it found"
 * cycle: sap_atc_run -> for each finding with a quickfix marker, sap_atc_quickfix_evaluate
 * -> sap_atc_quickfix_apply (proposal only). Every step is a direct call to the existing
 * tool's execute() against the same client -- no new ADT endpoint.
 *
 * apply defaults to false (propose-only, same safety stance as sap_atc_quickfix_apply
 * itself). When apply=true, each accepted proposal is written back sequentially via
 * sap_set_source (which already does lock->write->activate->unlock) -- never silently;
 * the response always lists exactly what was applied.
 */
public class AtcAutofixTool extends AbstractMcpTool {

    public static final String NAME = "sap_atc_autofix";

    public AtcAutofixTool(AdtRestClient client) {
        super(client);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return "Combo de auto-fix de ATC: roda sap_atc_run, depois para cada finding com quickfix "
                + "disponível, chama sap_atc_quickfix_evaluate + sap_atc_quickfix_apply (propostas, nunca "
                + "inventadas). Com apply=false (default), só retorna as propostas. Com apply=true, escreve "
                + "cada proposta aceita via sap_set_source, sequencialmente, sempre relatando o que mudou.";
    }

    @Override
    public JsonObject getInputSchema() {
        JsonObject variantProp = new JsonObject();
        variantProp.addProperty("type", "string");
        variantProp.addProperty("description", "ATC check variant (default: DEFAULT).");

        JsonObject maxFindingsProp = new JsonObject();
        maxFindingsProp.addProperty("type", "number");
        maxFindingsProp.addProperty("description", "Máximo de findings a processar com quickfix (default: 5).");

        JsonObject applyProp = new JsonObject();
        applyProp.addProperty("type", "boolean");
        applyProp.addProperty("description", "Se true, escreve cada proposta aceita no SAP via sap_set_source. Default: false (só propõe).");

        JsonObject properties = new JsonObject();
        properties.add("objectType", AdtUrlResolver.buildTypeProperty());
        properties.add("objectName", AdtUrlResolver.buildNameProperty());
        properties.add("variant", variantProp);
        properties.add("maxFindings", maxFindingsProp);
        properties.add("apply", applyProp);

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
        String objectType = optString(arguments, "objectType");
        String objectName = optString(arguments, "objectName");
        if (objectType == null || objectName == null) {
            throw new IllegalArgumentException("Provide objectType + objectName.");
        }

        boolean apply = arguments.has("apply") && arguments.get("apply").getAsBoolean();
        int maxFindings = optInt(arguments, "maxFindings", 5);

        JsonObject runArgs = new JsonObject();
        runArgs.addProperty("objectType", objectType);
        runArgs.addProperty("objectName", objectName);
        String variant = optString(arguments, "variant");
        if (variant != null && !variant.isEmpty()) {
            runArgs.addProperty("variant", variant);
        }

        String runResult = new AtcRunTool(client).execute(runArgs);
        JsonObject runJson = JsonParser.parseString(runResult).getAsJsonObject();
        JsonArray findings = runJson.has("findings") ? runJson.getAsJsonArray("findings") : new JsonArray();

        JsonArray fixes = new JsonArray();
        String currentSource = null;
        int processed = 0;

        for (JsonElement findingEl : findings) {
            if (processed >= maxFindings) break;
            JsonObject finding = findingEl.getAsJsonObject();
            String quickfixInfo = finding.has("quickfixInfo") ? finding.get("quickfixInfo").getAsString() : "";
            if (quickfixInfo.isEmpty()) continue;
            processed++;

            JsonObject fixEntry = new JsonObject();
            fixEntry.add("finding", finding);

            try {
                JsonObject evalArgs = new JsonObject();
                evalArgs.addProperty("objectType", objectType);
                evalArgs.addProperty("objectName", objectName);
                evalArgs.addProperty("markerId", quickfixInfo);
                String evalResult = new AtcQuickfixEvaluateTool(client).execute(evalArgs);
                JsonObject evalJson = JsonParser.parseString(evalResult).getAsJsonObject();
                JsonArray quickfixes = evalJson.has("quickfixes") ? evalJson.getAsJsonArray("quickfixes") : new JsonArray();

                if (quickfixes.size() == 0) {
                    fixEntry.addProperty("note", "SAP não ofereceu quickfix pra este finding.");
                    fixes.add(fixEntry);
                    continue;
                }

                JsonObject firstQuickfix = quickfixes.get(0).getAsJsonObject();
                String quickfixId = firstQuickfix.has("quickfixId") ? firstQuickfix.get("quickfixId").getAsString() : null;
                if (quickfixId == null || quickfixId.isEmpty()) {
                    fixEntry.addProperty("note", "Quickfix retornado sem quickfixId, pulando.");
                    fixes.add(fixEntry);
                    continue;
                }

                if (currentSource == null) {
                    JsonObject sourceArgs = new JsonObject();
                    sourceArgs.addProperty("objectType", objectType);
                    sourceArgs.addProperty("objectName", objectName);
                    currentSource = new GetSourceTool(client).execute(sourceArgs);
                }

                JsonObject applyArgs = new JsonObject();
                applyArgs.addProperty("objectType", objectType);
                applyArgs.addProperty("objectName", objectName);
                applyArgs.addProperty("quickfixId", quickfixId);
                applyArgs.addProperty("markerId", quickfixInfo);
                applyArgs.addProperty("sourceCode", currentSource);
                String applyResult = new AtcQuickfixApplyTool(client).execute(applyArgs);
                JsonObject applyJson = JsonParser.parseString(applyResult).getAsJsonObject();
                fixEntry.add("proposal", applyJson);

                if (apply && applyJson.has("proposedSource") && !applyJson.get("proposedSource").getAsString().isEmpty()) {
                    String proposedSource = applyJson.get("proposedSource").getAsString();
                    JsonObject setArgs = new JsonObject();
                    setArgs.addProperty("objectType", objectType);
                    setArgs.addProperty("objectName", objectName);
                    setArgs.addProperty("source", proposedSource);
                    String setResult = new SetSourceTool(client).execute(setArgs);
                    fixEntry.add("applied", JsonParser.parseString(setResult));
                    currentSource = proposedSource;
                }
            } catch (Exception e) {
                fixEntry.addProperty("error", e.getMessage());
            }

            fixes.add(fixEntry);
        }

        JsonObject output = new JsonObject();
        output.addProperty("totalFindings", findings.size());
        output.addProperty("processedFindings", processed);
        output.addProperty("apply", apply);
        output.add("fixes", fixes);
        return output.toString();
    }
}
