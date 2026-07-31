package com.sap.adt.mcp.tools;

import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sap.adt.mcp.sap.AdtRestClient;

/**
 * Tool: sap_compare_source -- Unified diff between two ABAP objects' source code.
 */
public class CompareSourceTool extends AbstractMcpTool {

    public static final String NAME = "sap_compare_source";

    public CompareSourceTool(AdtRestClient client) {
        super(client);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return "Show unified diff between two ABAP objects' source code. "
                + "Useful for comparing versions, reviewing changes, or comparing similar objects.";
    }

    @Override
    public JsonObject getInputSchema() {
        JsonObject type1 = new JsonObject();
        type1.addProperty("type", "string");
        type1.addProperty("description", "Object type of first object (e.g. CLAS/OC, PROG/P)");

        JsonObject name1 = new JsonObject();
        name1.addProperty("type", "string");
        name1.addProperty("description", "Name of first object");

        JsonObject type2 = new JsonObject();
        type2.addProperty("type", "string");
        type2.addProperty("description", "Object type of second object");

        JsonObject name2 = new JsonObject();
        name2.addProperty("type", "string");
        name2.addProperty("description", "Name of second object");

        JsonObject contextProp = new JsonObject();
        contextProp.addProperty("type", "integer");
        contextProp.addProperty("description", "Context lines around each change (like diff -U N). Default: 3");

        JsonObject properties = new JsonObject();
        properties.add("objectType1", type1);
        properties.add("objectName1", name1);
        properties.add("objectType2", type2);
        properties.add("objectName2", name2);
        properties.add("contextLines", contextProp);

        JsonArray required = new JsonArray();
        required.add("objectType1");
        required.add("objectName1");
        required.add("objectType2");
        required.add("objectName2");

        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.add("properties", properties);
        schema.add("required", required);
        return schema;
    }

    @Override
    public String execute(JsonObject arguments) throws Exception {
        String type1 = arguments.get("objectType1").getAsString();
        String objName1 = arguments.get("objectName1").getAsString();
        String type2 = arguments.get("objectType2").getAsString();
        String objName2 = arguments.get("objectName2").getAsString();
        int contextLines = optInt(arguments, "contextLines", 3);

        String url1 = AdtUrlResolver.resolveSourceUrl(type1, objName1);
        String url2 = AdtUrlResolver.resolveSourceUrl(type2, objName2);

        if (url1 == null) {
            throw new IllegalArgumentException("Cannot resolve source URL for " + type1 + "/" + objName1);
        }
        if (url2 == null) {
            throw new IllegalArgumentException("Cannot resolve source URL for " + type2 + "/" + objName2);
        }

        HttpResponse<String> resp1 = client.get(url1, "text/plain");
        HttpResponse<String> resp2 = client.get(url2, "text/plain");

        String src1 = resp1.body();
        String src2 = resp2.body();

        String label1 = objName1.toUpperCase() + " (" + type1 + ")";
        String label2 = objName2.toUpperCase() + " (" + type2 + ")";

        String diff = unifiedDiff(label1, src1, label2, src2, contextLines);

        JsonObject result = new JsonObject();
        result.addProperty("object1", label1);
        result.addProperty("object2", label2);
        result.addProperty("diff", diff);
        return result.toString();
    }

    /**
     * Produces a unified diff between two strings using LCS.
     */
    private static String unifiedDiff(String name1, String src1, String name2, String src2, int ctx) {
        String[] a = src1.split("\n", -1);
        String[] b = src2.split("\n", -1);

        // Build edit script via LCS
        int m = a.length, n = b.length;
        // For large files, limit LCS computation
        if (m > 3000 || n > 3000) {
            return "--- " + name1 + "\n+++ " + name2
                    + "\n(files too large for diff, " + m + " vs " + n + " lines)";
        }

        int[][] dp = new int[m + 1][n + 1];
        for (int i = m - 1; i >= 0; i--) {
            for (int j = n - 1; j >= 0; j--) {
                dp[i][j] = a[i].equals(b[j])
                        ? dp[i + 1][j + 1] + 1
                        : Math.max(dp[i + 1][j], dp[i][j + 1]);
            }
        }

        // Build per-line operations: ' ' keep, '-' remove, '+' add
        List<String> ops = new ArrayList<>();
        List<String> texts = new ArrayList<>();
        int i = 0, j = 0;
        while (i < m || j < n) {
            if (i < m && j < n && a[i].equals(b[j])) {
                ops.add(" "); texts.add(a[i]); i++; j++;
            } else if (j < n && (i >= m || dp[i + 1][j] >= dp[i][j + 1])) {
                ops.add("+"); texts.add(b[j]); j++;
            } else {
                ops.add("-"); texts.add(a[i]); i++;
            }
        }

        // Identify changed regions and emit hunks with context
        int size = ops.size();
        boolean[] changed = new boolean[size];
        for (int k = 0; k < size; k++) {
            if (!ops.get(k).equals(" ")) changed[k] = true;
        }

        // Mark context window
        boolean[] inHunk = new boolean[size];
        for (int k = 0; k < size; k++) {
            if (!changed[k]) continue;
            for (int d = Math.max(0, k - ctx); d <= Math.min(size - 1, k + ctx); d++) {
                inHunk[d] = true;
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("--- ").append(name1).append("\n");
        sb.append("+++ ").append(name2).append("\n");

        boolean anyDiff = false;
        for (boolean c : changed) if (c) { anyDiff = true; break; }
        if (!anyDiff) {
            sb.append("(identical)\n");
            return sb.toString();
        }

        int k = 0;
        while (k < size) {
            if (!inHunk[k]) { k++; continue; }

            // Start of hunk — compute line numbers
            int hunkStart = k;
            while (k < size && inHunk[k]) k++;
            int hunkEnd = k;

            // Count source/dest line numbers up to hunkStart
            int aLine = 1, bLine = 1;
            for (int x = 0; x < hunkStart; x++) {
                if (!ops.get(x).equals("+")) aLine++;
                if (!ops.get(x).equals("-")) bLine++;
            }
            int aCount = 0, bCount = 0;
            for (int x = hunkStart; x < hunkEnd; x++) {
                if (!ops.get(x).equals("+")) aCount++;
                if (!ops.get(x).equals("-")) bCount++;
            }

            sb.append("@@ -").append(aLine).append(",").append(aCount)
              .append(" +").append(bLine).append(",").append(bCount).append(" @@\n");

            for (int x = hunkStart; x < hunkEnd; x++) {
                sb.append(ops.get(x)).append(texts.get(x)).append("\n");
            }
        }

        return sb.toString();
    }
}
