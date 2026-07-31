package com.sap.adt.mcp.tools;

import java.net.http.HttpResponse;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sap.adt.mcp.sap.AdtRestClient;

/**
 * Tool: sap_edit_source -- Surgical string replacement in ABAP source code.
 *
 * Fetches the current source, applies the replacement, then lock-write-unlock-activate
 * using the same flow as sap_set_source. Unlike sap_set_source, this never requires the
 * caller to supply the full file content.
 */
public class EditSourceTool extends AbstractMcpTool {

    public static final String NAME = "sap_edit_source";

    public EditSourceTool(AdtRestClient client) {
        super(client);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return "Surgical string replacement in ABAP source code. "
                + "Fetches current source, replaces old_string with new_string, then saves and activates. "
                + "Much safer than sap_set_source for incremental edits — no risk of accidentally deleting code. "
                + "Provide objectType + objectName (e.g. CLAS/OC + ZCL_MY_CLASS). "
                + "For classes use 'include' to edit localtypes, testclasses or macros sections.";
    }

    @Override
    public JsonObject getInputSchema() {
        JsonObject oldStringProp = new JsonObject();
        oldStringProp.addProperty("type", "string");
        oldStringProp.addProperty("description",
                "Exact string to find. Must be unique in the source if replace_all is false. "
                + "Include enough surrounding context to make it unique.");

        JsonObject newStringProp = new JsonObject();
        newStringProp.addProperty("type", "string");
        newStringProp.addProperty("description", "Replacement string. May be empty to delete old_string.");

        JsonObject replaceAllProp = new JsonObject();
        replaceAllProp.addProperty("type", "boolean");
        replaceAllProp.addProperty("description",
                "If true, replace all occurrences. If false (default), fail if old_string appears more than once.");

        JsonObject caseInsensitiveProp = new JsonObject();
        caseInsensitiveProp.addProperty("type", "boolean");
        caseInsensitiveProp.addProperty("description", "Case-insensitive match. Default: false");

        JsonObject includeProp = new JsonObject();
        includeProp.addProperty("type", "string");
        JsonArray includeEnum = new JsonArray();
        includeEnum.add("main"); includeEnum.add("definitions");
        includeEnum.add("implementations"); includeEnum.add("testclasses"); includeEnum.add("macros");
        includeProp.add("enum", includeEnum);
        includeProp.addProperty("description",
                "Class include section (CLAS/OC only). Default: main (global class). "
                + "definitions = local class definitions (Local Types tab - 1st part), "
                + "implementations = local class implementations (Local Types tab - 2nd part), "
                + "testclasses = Test Methods tab, macros = Macros tab.");

        JsonObject transportProp = new JsonObject();
        transportProp.addProperty("type", "string");
        transportProp.addProperty("description", "Optional transport request number (e.g. 'DEVK900123')");

        JsonObject properties = new JsonObject();
        properties.add("objectType", AdtUrlResolver.buildTypeProperty());
        properties.add("objectName", AdtUrlResolver.buildNameProperty());
        properties.add("include", includeProp);
        properties.add("oldString", oldStringProp);
        properties.add("newString", newStringProp);
        properties.add("replaceAll", replaceAllProp);
        properties.add("caseInsensitive", caseInsensitiveProp);
        properties.add("transport", transportProp);

        JsonArray required = new JsonArray();
        required.add("objectType");
        required.add("objectName");
        required.add("oldString");
        required.add("newString");

        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.add("properties", properties);
        schema.add("required", required);
        return schema;
    }

    @Override
    public String execute(JsonObject arguments) throws Exception {
        String sourceUrl = resolveSourceUrlArg(arguments, "objectSourceUrl");
        if (sourceUrl == null) {
            throw new IllegalArgumentException("Provide objectType + objectName.");
        }
        sourceUrl = ensureSourceUrl(sourceUrl);
        sourceUrl = GetSourceTool.applyInclude(sourceUrl, optString(arguments, "include"));

        String oldString = arguments.get("oldString").getAsString();
        String newString = arguments.get("newString").getAsString();
        boolean replaceAll = arguments.has("replaceAll") && arguments.get("replaceAll").getAsBoolean();
        boolean caseInsensitive = arguments.has("caseInsensitive")
                && arguments.get("caseInsensitive").getAsBoolean();
        String transport = optString(arguments, "transport");

        // Fetch current source
        HttpResponse<String> getResp = client.get(sourceUrl, "text/plain");
        String source = getResp.body();

        // Count occurrences
        int count = countOccurrences(source, oldString, caseInsensitive);
        if (count == 0) {
            throw new IllegalArgumentException(
                    "old_string not found in source. Check spelling and whitespace.");
        }
        if (!replaceAll && count > 1) {
            throw new IllegalArgumentException(
                    "old_string appears " + count + " times. Either make it more unique "
                    + "or set replaceAll=true to replace all occurrences.");
        }

        String newSource = replaceString(source, oldString, newString, caseInsensitive, replaceAll);

        String writeResult = AdtSourceWriter.lockWriteUnlock(client, sourceUrl, newSource, transport);

        JsonObject result = new JsonObject();
        result.addProperty("replacements", count);
        result.addProperty("writeResult", writeResult);
        return result.toString();
    }

    private static int countOccurrences(String source, String target, boolean caseInsensitive) {
        String s = caseInsensitive ? source.toLowerCase() : source;
        String t = caseInsensitive ? target.toLowerCase() : target;
        int count = 0, idx = 0;
        while ((idx = s.indexOf(t, idx)) != -1) {
            count++;
            idx += t.length();
        }
        return count;
    }

    private static String replaceString(String source, String oldStr, String newStr,
            boolean caseInsensitive, boolean replaceAll) {
        if (!caseInsensitive) {
            return replaceAll ? source.replace(oldStr, newStr)
                              : source.replaceFirst(java.util.regex.Pattern.quote(oldStr),
                                      java.util.regex.Matcher.quoteReplacement(newStr));
        }
        // Case-insensitive: manual scan
        StringBuilder sb = new StringBuilder();
        String lowerSource = source.toLowerCase();
        String lowerOld = oldStr.toLowerCase();
        int start = 0, idx;
        while ((idx = lowerSource.indexOf(lowerOld, start)) != -1) {
            sb.append(source, start, idx);
            sb.append(newStr);
            start = idx + oldStr.length();
            if (!replaceAll) break;
        }
        sb.append(source, start, source.length());
        return sb.toString();
    }
}
