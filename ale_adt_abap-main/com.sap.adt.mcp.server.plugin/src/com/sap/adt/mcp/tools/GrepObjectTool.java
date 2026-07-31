package com.sap.adt.mcp.tools;

import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sap.adt.mcp.sap.AdtRestClient;

/**
 * Tool: sap_grep_object -- Regex search in a single ABAP object's source code.
 */
public class GrepObjectTool extends AbstractMcpTool {

    public static final String NAME = "sap_grep_object";

    public GrepObjectTool(AdtRestClient client) {
        super(client);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return "Search for a regex pattern in an ABAP object's source code. "
                + "Returns matches with line numbers and optional context lines. "
                + "Provide objectType + objectName (e.g. CLAS/OC + ZCL_MY_CLASS). "
                + "For classes use 'include' to search localtypes, testclasses or macros sections.";
    }

    @Override
    public JsonObject getInputSchema() {
        JsonObject patternProp = new JsonObject();
        patternProp.addProperty("type", "string");
        patternProp.addProperty("description", "Java regex pattern (e.g. 'SELECT.*FROM', 'TODO', 'lv_\\\\w+')");

        JsonObject contextProp = new JsonObject();
        contextProp.addProperty("type", "integer");
        contextProp.addProperty("description", "Number of lines to show before/after each match (like grep -C). Default: 0");

        JsonObject caseInsensitiveProp = new JsonObject();
        caseInsensitiveProp.addProperty("type", "boolean");
        caseInsensitiveProp.addProperty("description", "Case-insensitive matching. Default: false");

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

        JsonObject properties = new JsonObject();
        properties.add("objectType", AdtUrlResolver.buildTypeProperty());
        properties.add("objectName", AdtUrlResolver.buildNameProperty());
        properties.add("pattern", patternProp);
        properties.add("include", includeProp);
        properties.add("contextLines", contextProp);
        properties.add("caseInsensitive", caseInsensitiveProp);

        JsonArray required = new JsonArray();
        required.add("objectType");
        required.add("objectName");
        required.add("pattern");

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
        sourceUrl = GetSourceTool.applyInclude(sourceUrl, optString(arguments, "include"));

        String patternStr = arguments.get("pattern").getAsString();
        int contextLines = optInt(arguments, "contextLines", 0);
        boolean caseInsensitive = arguments.has("caseInsensitive")
                && arguments.get("caseInsensitive").getAsBoolean();

        HttpResponse<String> resp = client.get(sourceUrl, "text/plain");
        String source = resp.body();

        int flags = caseInsensitive ? Pattern.CASE_INSENSITIVE | Pattern.DOTALL : Pattern.DOTALL;
        Pattern pattern = Pattern.compile(patternStr, flags);

        String[] lines = source.split("\n", -1);
        JsonArray matches = new JsonArray();
        List<Integer> matchedLines = new ArrayList<>();

        for (int i = 0; i < lines.length; i++) {
            Matcher m = pattern.matcher(lines[i]);
            if (m.find()) {
                matchedLines.add(i);
            }
        }

        // Group consecutive match windows to avoid duplicates
        boolean[] inWindow = new boolean[lines.length];
        for (int lineIdx : matchedLines) {
            int from = Math.max(0, lineIdx - contextLines);
            int to = Math.min(lines.length - 1, lineIdx + contextLines);
            for (int k = from; k <= to; k++) inWindow[k] = true;
        }

        // Build output chunks
        int i = 0;
        while (i < lines.length) {
            if (!inWindow[i]) { i++; continue; }

            // Find contiguous window
            int start = i;
            while (i < lines.length && inWindow[i]) i++;
            int end = i; // exclusive

            JsonObject chunk = new JsonObject();
            chunk.addProperty("startLine", start + 1);
            StringBuilder sb = new StringBuilder();
            for (int k = start; k < end; k++) {
                boolean isMatch = matchedLines.contains(k);
                sb.append(isMatch ? ">" : " ")
                  .append(String.format("%5d", k + 1))
                  .append(": ")
                  .append(lines[k]);
                if (k < end - 1) sb.append("\n");
            }
            chunk.addProperty("text", sb.toString());
            matches.add(chunk);
        }

        JsonObject result = new JsonObject();
        result.addProperty("pattern", patternStr);
        result.addProperty("totalMatches", matchedLines.size());
        result.add("matches", matches);
        return result.toString();
    }
}
