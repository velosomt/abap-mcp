package com.sap.adt.mcp.tools;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sap.adt.mcp.sap.AdtRestClient;

/**
 * Tool: sap_explain_object -- Orchestrator that aggregates sap_object_structure +
 * sap_get_source (+ optionally sap_usage_references / sap_get_enhancements) for one
 * object into a single response, for the "understand an object before touching it"
 * workflow that otherwise takes 2-4 separate calls.
 *
 * Pure composition: every field below comes from an existing tool's own execute(),
 * called with the same client -- no new ADT endpoint is introduced here.
 */
public class ExplainObjectTool extends AbstractMcpTool {

    public static final String NAME = "sap_explain_object";

    public ExplainObjectTool(AdtRestClient client) {
        super(client);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return "Combo de leitura: junta sap_object_structure + sap_get_source num único retorno, "
                + "e opcionalmente sap_usage_references (includeUsage) e sap_get_enhancements "
                + "(includeEnhancements). Útil pra entender um objeto de uma vez só antes de editar.";
    }

    @Override
    public JsonObject getInputSchema() {
        JsonObject includeUsageProp = new JsonObject();
        includeUsageProp.addProperty("type", "boolean");
        includeUsageProp.addProperty("description", "Incluir sap_usage_references (onde usado). Default: false.");

        JsonObject includeEnhancementsProp = new JsonObject();
        includeEnhancementsProp.addProperty("type", "boolean");
        includeEnhancementsProp.addProperty("description", "Incluir sap_get_enhancements (BAdI/enhancement points). Default: false.");

        JsonObject properties = new JsonObject();
        properties.add("objectType", AdtUrlResolver.buildTypeProperty());
        properties.add("objectName", AdtUrlResolver.buildNameProperty());
        properties.add("includeUsage", includeUsageProp);
        properties.add("includeEnhancements", includeEnhancementsProp);

        com.google.gson.JsonArray required = new com.google.gson.JsonArray();
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

        JsonObject output = new JsonObject();
        output.addProperty("objectType", objectType);
        output.addProperty("objectName", objectName);

        try {
            String structureResult = new ObjectStructureTool(client).execute(arguments);
            output.add("structure", JsonParser.parseString(structureResult));
        } catch (Exception e) {
            output.addProperty("structureError", e.getMessage());
        }

        try {
            String source = new GetSourceTool(client).execute(arguments);
            output.addProperty("source", source);
        } catch (Exception e) {
            output.addProperty("sourceError", e.getMessage());
        }

        boolean includeUsage = arguments.has("includeUsage") && arguments.get("includeUsage").getAsBoolean();
        if (includeUsage) {
            try {
                String usageResult = new UsageReferencesTool(client).execute(arguments);
                output.add("usage", JsonParser.parseString(usageResult));
            } catch (Exception e) {
                output.addProperty("usageError", e.getMessage());
            }
        }

        boolean includeEnhancements = arguments.has("includeEnhancements")
                && arguments.get("includeEnhancements").getAsBoolean();
        if (includeEnhancements) {
            try {
                String enhancementsResult = new GetEnhancementsTool(client).execute(arguments);
                output.add("enhancements", JsonParser.parseString(enhancementsResult));
            } catch (Exception e) {
                output.addProperty("enhancementsError", e.getMessage());
            }
        }

        return output.toString();
    }
}
