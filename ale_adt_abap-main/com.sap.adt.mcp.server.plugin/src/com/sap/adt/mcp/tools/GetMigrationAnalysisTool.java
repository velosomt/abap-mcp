package com.sap.adt.mcp.tools;

import java.net.http.HttpResponse;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sap.adt.mcp.sap.AdtRestClient;
import com.sap.adt.mcp.sap.AdtXmlParser;

/**
 * Tool: sap_get_migration_analysis -- Custom-code migration analysis, ported 1:1
 * from the AWS SAP ABAP Accelerator MCP (get_migration_analysis), INCLUDING its
 * fallback behavior: /sap/bc/adt/migration/analysis is not a documented/standard
 * SAP ADT API, and on most backends it errors out. When that happens, the AWS
 * original silently returns pre-canned, hardcoded findings per object type instead
 * of a real analysis -- a behavior the team would otherwise have excluded entirely
 * as too risky for a Clean Core/migration decision context.
 *
 * Per explicit user decision, this is ported identical to AWS (mock fallback
 * included), but every response carries a "mocked" flag and, when true, a
 * "warning" field that must be surfaced verbatim -- never present mocked output
 * as if it were a real finding.
 */
public class GetMigrationAnalysisTool extends AbstractMcpTool {

    public static final String NAME = "sap_get_migration_analysis";

    private static final String MOCK_WARNING =
            "AVISO: o endpoint real de analise de migracao (/sap/bc/adt/migration/analysis) falhou ou nao "
            + "esta disponivel neste backend (nao e uma API ADT padrao/documentada). Os achados abaixo sao "
            + "DADOS FIXOS de demonstracao (mesmo comportamento do AWS SAP ABAP Accelerator MCP original) -- "
            + "NAO sao uma analise real deste objeto. Nao use isso como base para nenhuma decisao de Clean "
            + "Core/migracao.";

    public GetMigrationAnalysisTool(AdtRestClient client) {
        super(client);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return "Custom-code migration analysis for an ABAP object. WARNING: the underlying SAP endpoint is "
                + "not a standard ADT API and commonly fails -- when it does, this returns hardcoded "
                + "demonstration findings instead of a real analysis, flagged via \"mocked\": true. Never "
                + "treat mocked=true output as a real compliance result; always surface the \"warning\" field "
                + "to the user when present.";
    }

    @Override
    public JsonObject getInputSchema() {
        JsonObject properties = new JsonObject();
        properties.add("objectType", AdtUrlResolver.buildTypeProperty());
        properties.add("objectName", AdtUrlResolver.buildNameProperty());

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
        if (objectType == null || objectType.isEmpty() || objectName == null || objectName.isEmpty()) {
            throw new IllegalArgumentException("Provide objectType + objectName.");
        }

        String analysisXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<migration:analysisRequest xmlns:migration=\"http://www.sap.com/adt/migration\" "
                + "xmlns:adtcore=\"http://www.sap.com/adt/core\">"
                + "<migration:object adtcore:name=\"" + escapeXml(objectName.toUpperCase())
                + "\" adtcore:type=\"" + escapeXml(objectType) + "\"/>"
                + "</migration:analysisRequest>";

        JsonObject output;
        try {
            HttpResponse<String> response = client.post(
                    "/sap/bc/adt/migration/analysis", analysisXml, "application/xml", "application/xml");
            if (response.statusCode() == 200) {
                output = AdtXmlParser.parseMigrationAnalysis(response.body());
                output.addProperty("mocked", false);
            } else {
                output = buildMockAnalysis(objectType);
                output.addProperty("mocked", true);
                output.addProperty("warning", MOCK_WARNING);
            }
        } catch (Exception e) {
            output = buildMockAnalysis(objectType);
            output.addProperty("mocked", true);
            output.addProperty("warning", MOCK_WARNING);
        }

        output.addProperty("objectName", objectName.toUpperCase());
        output.addProperty("objectType", objectType);
        return output.toString();
    }

    /**
     * Hardcoded findings mirroring AWS's _get_mock_migration_analysis exactly (same
     * text/lines/recommendations per object type) -- intentional 1:1 fidelity, not
     * an approximation, per the explicit decision to port this tool identical to AWS.
     */
    private JsonObject buildMockAnalysis(String objectType) {
        JsonObject analysis = new JsonObject();
        JsonArray issues = new JsonArray();
        JsonArray recommendations = new JsonArray();
        JsonArray dependencies = new JsonArray();
        analysis.add("compatibilityIssues", issues);
        analysis.add("migrationRecommendations", recommendations);
        analysis.add("dependencies", dependencies);
        analysis.addProperty("effortEstimate", "Medium");

        String type = objectType == null ? "" : objectType.toUpperCase();
        String bareType = type.contains("/") ? type.substring(0, type.indexOf('/')) : type;

        if ("CLAS".equals(bareType)) {
            issues.add(mockIssue("WARNING", "Class uses deprecated method CALL FUNCTION", 45));
            issues.add(mockIssue("INFO", "Consider using modern ABAP syntax", 78));
            recommendations.add("Replace deprecated CALL FUNCTION with modern API calls");
            recommendations.add("Update to use ABAP 7.5+ syntax features");
            recommendations.add("Consider implementing interfaces for better modularity");
            dependencies.add("STANDARD_CLASS");
            dependencies.add("UTILITY_FUNCTIONS");
        } else if ("PROG".equals(bareType)) {
            issues.add(mockIssue("ERROR", "Program uses obsolete statement MOVE", 23));
            recommendations.add("Replace MOVE statements with assignment operator (=)");
            recommendations.add("Modernize data declarations using DATA() inline declarations");
            analysis.addProperty("effortEstimate", "Low");
        } else if ("DDLS".equals(bareType)) {
            issues.add(mockIssueNoLine("INFO", "CDS view is already modern - no issues found"));
            recommendations.add("CDS view follows modern SAP development practices");
            recommendations.add("Consider adding annotations for better metadata");
            analysis.addProperty("effortEstimate", "None");
        } else {
            issues.add(mockIssueNoLine("INFO", "No specific migration issues identified for " + objectType));
            recommendations.add("Review object for modern ABAP best practices");
            recommendations.add("Consider refactoring for better maintainability");
        }

        return analysis;
    }

    private JsonObject mockIssue(String severity, String message, int line) {
        JsonObject issue = new JsonObject();
        issue.addProperty("severity", severity);
        issue.addProperty("message", message);
        issue.addProperty("line", line);
        return issue;
    }

    private JsonObject mockIssueNoLine(String severity, String message) {
        JsonObject issue = new JsonObject();
        issue.addProperty("severity", severity);
        issue.addProperty("message", message);
        issue.add("line", com.google.gson.JsonNull.INSTANCE);
        return issue;
    }
}
