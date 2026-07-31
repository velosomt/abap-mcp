package com.sap.adt.mcp.tools;

import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sap.adt.mcp.sap.AdtRestClient;
import com.sap.adt.mcp.sap.AdtXmlParser;

/**
 * Tool: sap_grep_package -- Regex search across all source objects in an ABAP package.
 */
public class GrepPackageTool extends AbstractMcpTool {

    public static final String NAME = "sap_grep_package";

    // Object types that expose plain-text source via ADT
    private static final java.util.Set<String> SOURCE_TYPES = new java.util.HashSet<>(java.util.Arrays.asList(
            "PROG/P", "PROG/I", "CLAS/OC", "INTF/OI", "FUGR/FF",
            "DDLS/DF", "BDEF/BO", "BDEF/BDO", "SRVD/SRV", "DCLS/DL", "DDLX/EX"
    ));

    public GrepPackageTool(AdtRestClient client) {
        super(client);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return "Search for a regex pattern across all source objects in an ABAP package. "
                + "Returns matches grouped by object. Covers programs, classes, interfaces, CDS views, etc.";
    }

    @Override
    public JsonObject getInputSchema() {
        JsonObject packageProp = new JsonObject();
        packageProp.addProperty("type", "string");
        packageProp.addProperty("description", "Package name (e.g. '$TMP', 'ZPACKAGE')");

        JsonObject patternProp = new JsonObject();
        patternProp.addProperty("type", "string");
        patternProp.addProperty("description", "Java regex pattern (e.g. 'SELECT.*FROM', 'TODO', 'lv_\\\\w+')");

        JsonObject maxResultsProp = new JsonObject();
        maxResultsProp.addProperty("type", "integer");
        maxResultsProp.addProperty("description", "Max number of objects to search. Default: 50");

        JsonObject caseInsensitiveProp = new JsonObject();
        caseInsensitiveProp.addProperty("type", "boolean");
        caseInsensitiveProp.addProperty("description", "Case-insensitive matching. Default: false");

        JsonObject contextProp = new JsonObject();
        contextProp.addProperty("type", "integer");
        contextProp.addProperty("description", "Context lines before/after each match. Default: 0");

        JsonObject properties = new JsonObject();
        properties.add("packageName", packageProp);
        properties.add("pattern", patternProp);
        properties.add("maxResults", maxResultsProp);
        properties.add("caseInsensitive", caseInsensitiveProp);
        properties.add("contextLines", contextProp);

        JsonArray required = new JsonArray();
        required.add("packageName");
        required.add("pattern");

        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.add("properties", properties);
        schema.add("required", required);
        return schema;
    }

    @Override
    public String execute(JsonObject arguments) throws Exception {
        String packageName = arguments.get("packageName").getAsString().toUpperCase();
        String patternStr = arguments.get("pattern").getAsString();
        int maxResults = optInt(arguments, "maxResults", 50);
        boolean caseInsensitive = arguments.has("caseInsensitive")
                && arguments.get("caseInsensitive").getAsBoolean();
        int contextLines = optInt(arguments, "contextLines", 0);

        int flags = caseInsensitive ? Pattern.CASE_INSENSITIVE | Pattern.DOTALL : Pattern.DOTALL;
        Pattern pattern = Pattern.compile(patternStr, flags);

        // Get package tree
        String treePath = "/sap/bc/adt/repository/nodestructure"
                + "?parent_type=" + urlEncode("DEVC/K")
                + "&parent_name=" + urlEncode(packageName)
                + "&withShortDescriptions=true";

        HttpResponse<String> treeResp = client.postWithHeaders(treePath, "",
                "application/*", "application/*", STATEFUL_HEADERS);

        JsonArray nodes = AdtXmlParser.parseNodeStructure(treeResp.body());

        JsonArray objectResults = new JsonArray();
        int searched = 0;

        for (int ni = 0; ni < nodes.size() && searched < maxResults; ni++) {
            JsonObject node = nodes.get(ni).getAsJsonObject();
            String objType = node.has("objectType") ? node.get("objectType").getAsString() : "";
            String objName = node.has("objectName") ? node.get("objectName").getAsString() : "";

            if (!SOURCE_TYPES.contains(objType) || objName.isEmpty()) continue;

            String sourceUrl = AdtUrlResolver.resolveSourceUrl(objType, objName);
            if (sourceUrl == null) continue;

            searched++;
            try {
                HttpResponse<String> srcResp = client.get(sourceUrl, "text/plain");
                String source = srcResp.body();
                List<Integer> matchedLines = new ArrayList<>();
                String[] lines = source.split("\n", -1);
                for (int i = 0; i < lines.length; i++) {
                    if (pattern.matcher(lines[i]).find()) matchedLines.add(i);
                }
                if (matchedLines.isEmpty()) continue;

                JsonObject objResult = new JsonObject();
                objResult.addProperty("objectType", objType);
                objResult.addProperty("objectName", objName);
                objResult.addProperty("matchCount", matchedLines.size());

                JsonArray chunks = new JsonArray();
                boolean[] inWindow = new boolean[lines.length];
                for (int lineIdx : matchedLines) {
                    int from = Math.max(0, lineIdx - contextLines);
                    int to = Math.min(lines.length - 1, lineIdx + contextLines);
                    for (int k = from; k <= to; k++) inWindow[k] = true;
                }

                int i = 0;
                while (i < lines.length) {
                    if (!inWindow[i]) { i++; continue; }
                    int start = i;
                    while (i < lines.length && inWindow[i]) i++;
                    StringBuilder sb = new StringBuilder();
                    for (int k = start; k < i; k++) {
                        boolean isMatch = matchedLines.contains(k);
                        sb.append(isMatch ? ">" : " ")
                          .append(String.format("%5d", k + 1))
                          .append(": ")
                          .append(lines[k]);
                        if (k < i - 1) sb.append("\n");
                    }
                    JsonObject chunk = new JsonObject();
                    chunk.addProperty("startLine", start + 1);
                    chunk.addProperty("text", sb.toString());
                    chunks.add(chunk);
                }
                objResult.add("matches", chunks);
                objectResults.add(objResult);
            } catch (Exception e) {
                // Skip objects that can't be fetched
            }
        }

        JsonObject result = new JsonObject();
        result.addProperty("package", packageName);
        result.addProperty("pattern", patternStr);
        result.addProperty("objectsSearched", searched);
        result.addProperty("objectsWithMatches", objectResults.size());
        result.add("results", objectResults);
        return result.toString();
    }
}
