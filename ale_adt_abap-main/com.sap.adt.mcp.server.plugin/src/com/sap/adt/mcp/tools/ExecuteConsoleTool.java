package com.sap.adt.mcp.tools;

import java.net.http.HttpResponse;

import com.google.gson.JsonObject;
import com.sap.adt.mcp.sap.AdtRestClient;

/**
 * Tool to execute an ABAP class that implements IF_OO_ADT_CLASSRUN.
 * This acts as an "ABAP Console", allowing arbitrary code execution on the server.
 */
public class ExecuteConsoleTool extends AbstractMcpTool {

    public ExecuteConsoleTool(AdtRestClient client) {
        super(client);
    }

    @Override
    public String getName() {
        return "sap_execute_console";
    }

    @Override
    public String getDescription() {
        return "Executa uma classe ABAP que implementa IF_OO_ADT_CLASSRUN e retorna o output do console. Excelente para rodar scripts nativos de criação de CDS ou automações.";
    }

    @Override
    public JsonObject getInputSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");

        JsonObject properties = new JsonObject();

        JsonObject classNameProp = new JsonObject();
        classNameProp.addProperty("type", "string");
        classNameProp.addProperty("description", "Nome da classe ABAP que implementa IF_OO_ADT_CLASSRUN (ex: 'ZCL_MY_CONSOLE').");
        properties.add("className", classNameProp);

        schema.add("properties", properties);

        com.google.gson.JsonArray required = new com.google.gson.JsonArray();
        required.add("className");
        schema.add("required", required);

        return schema;
    }

    @Override
    public String execute(JsonObject arguments) throws Exception {
        String className = arguments.get("className").getAsString().toUpperCase();

        String path = "/sap/bc/adt/oo/classrun/" + className;
        
        // text/plain is the Accept header that actually works for classrun output (the
        // application/vnd.sap.adt.classrun.v1+xml variant returns HTTP 406 Not Acceptable).
        HttpResponse<String> response = client.post(path, "", "application/xml", "text/plain");

        JsonObject output = new JsonObject();
        output.addProperty("statusCode", response.statusCode());

        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            output.addProperty("status", "success");
            // Basic parsing of the XML output (extract text between <content> tags)
            String body = response.body();
            String consoleText = body;
            if (body.contains("<content>") && body.contains("</content>")) {
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
