package com.sap.adt.mcp.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sap.adt.mcp.sap.AdtRestClient;

/**
 * Tool: sap_replace_source_content -- Replace specific text blocks in ABAP source.
 */
public class ReplaceSourceContentTool extends AbstractMcpTool {

    public static final String NAME = "sap_replace_source_content";

    public ReplaceSourceContentTool(AdtRestClient client) {
        super(client);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return "Edits an existing ABAP object by replacing a specific block of code (targetContent) with new code (replacementContent). This is MUCH SAFER than sap_set_source for edits because it prevents accidental code deletion.";
    }

    @Override
    public JsonObject getInputSchema() {
        JsonObject targetProp = new JsonObject();
        targetProp.addProperty("type", "string");
        targetProp.addProperty("description", "The exact existing text block to be replaced. MUST MATCH EXACTLY with the current source code (including spaces).");

        JsonObject replacementProp = new JsonObject();
        replacementProp.addProperty("type", "string");
        replacementProp.addProperty("description", "The new text block to insert in place of targetContent.");

        JsonObject transportProp = new JsonObject();
        transportProp.addProperty("type", "string");
        transportProp.addProperty("description", "Optional transport request number (e.g. 'DEVK900123')");

        JsonObject properties = new JsonObject();
        properties.add("objectType", AdtUrlResolver.buildTypeProperty());
        properties.add("objectName", AdtUrlResolver.buildNameProperty());
        properties.add("targetContent", targetProp);
        properties.add("replacementContent", replacementProp);
        properties.add("transport", transportProp);

        JsonArray required = new JsonArray();
        required.add("objectType");
        required.add("objectName");
        required.add("targetContent");
        required.add("replacementContent");

        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.add("properties", properties);
        schema.add("required", required);

        return schema;
    }

    @Override
    public String execute(JsonObject arguments) throws Exception {
        String targetContent = arguments.get("targetContent").getAsString();
        String replacementContent = arguments.get("replacementContent").getAsString();
        
        // 1. Fetch current source
        GetSourceTool getTool = new GetSourceTool(client);
        String currentSource = getTool.execute(arguments);
        
        if (currentSource == null || currentSource.isEmpty()) {
            throw new RuntimeException("Could not read current source code. The object might be empty or invalid.");
        }
        
        // 2. Validate and Replace
        if (!currentSource.contains(targetContent)) {
            throw new RuntimeException("Target content not found in the source code! Please make sure you provided the EXACT matching text to replace. Context might be slightly different.");
        }
        
        String newSource = currentSource.replace(targetContent, replacementContent);
        
        // 3. Re-use SetSourceTool logic to lock/write/activate
        JsonObject setArgs = arguments.deepCopy();
        setArgs.addProperty("source", newSource);
        
        SetSourceTool setTool = new SetSourceTool(client);
        return setTool.execute(setArgs);
    }
}
