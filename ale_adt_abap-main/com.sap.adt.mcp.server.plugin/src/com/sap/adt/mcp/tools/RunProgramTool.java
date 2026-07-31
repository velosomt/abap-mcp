package com.sap.adt.mcp.tools;

import java.net.http.HttpResponse;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sap.adt.mcp.sap.AdtRestClient;

/**
 * Tool: sap_run_program -- Execute a classic ABAP report (executable PROG/P) via ADT
 * programrun and return its list/console output. Complements sap_execute_console, which
 * only runs classes implementing IF_OO_ADT_CLASSRUN -- this is for the (still common)
 * classic REPORT/SUBMIT-style programs.
 */
public class RunProgramTool extends AbstractMcpTool {

    public static final String NAME = "sap_run_program";

    public RunProgramTool(AdtRestClient client) {
        super(client);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return "Executa um programa ABAP executavel (REPORT classico) via /programs/programrun e retorna o "
            + "output gerado (ex: WRITE, listas classicas). Diferente de sap_execute_console, que so executa "
            + "classes que implementam IF_OO_ADT_CLASSRUN.";
    }

    @Override
    public JsonObject getInputSchema() {
        JsonObject programNameProp = new JsonObject();
        programNameProp.addProperty("type", "string");
        programNameProp.addProperty("description", "Nome do programa ABAP executavel (ex: 'ZMY_REPORT').");

        JsonObject properties = new JsonObject();
        properties.add("programName", programNameProp);

        JsonArray required = new JsonArray();
        required.add("programName");

        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.add("properties", properties);
        schema.add("required", required);

        return schema;
    }

    @Override
    public String execute(JsonObject arguments) throws Exception {
        String programName = optString(arguments, "programName");
        if (programName == null || programName.isEmpty()) {
            throw new IllegalArgumentException("Provide 'programName'.");
        }
        programName = programName.toUpperCase();

        String path = "/sap/bc/adt/programs/programrun/" + programName;

        // text/plain is the Accept header that actually works for programrun output (same
        // pattern as sap_execute_console's classrun -- the XML variant returns HTTP 406).
        HttpResponse<String> response = client.post(path, "", "application/xml", "text/plain");

        JsonObject output = new JsonObject();
        output.addProperty("statusCode", response.statusCode());

        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            output.addProperty("status", "success");
            String body = response.body();
            String consoleText = body;
            if (body != null && body.contains("<content>") && body.contains("</content>")) {
                consoleText = body.substring(body.indexOf("<content>") + 9, body.indexOf("</content>"));
            }
            output.addProperty("output", consoleText);
        } else {
            output.addProperty("status", "error");
            output.addProperty("errorBody", response.body());
        }

        return output.toString();
    }
}
