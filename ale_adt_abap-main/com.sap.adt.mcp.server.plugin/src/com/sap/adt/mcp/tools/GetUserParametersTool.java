package com.sap.adt.mcp.tools;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.sap.adt.mcp.sap.AdtRestClient;

/**
 * Tool: sap_get_user_parameters -- Read a user's persistent parameters (SPA/GPA, the SU3
 * "Parameters" tab / memory IDs), via CALL FUNCTION 'SUSR_USER_PARAMETERS_GET'. ADT has no
 * direct REST endpoint for this, so this tool creates a throwaway IF_OO_ADT_CLASSRUN helper
 * class that performs the call, runs it via classrun, parses the structured console output,
 * and (by default) deletes the helper class afterwards.
 */
public class GetUserParametersTool extends AbstractMcpTool {

    public static final String NAME = "sap_get_user_parameters";

    public GetUserParametersTool(AdtRestClient client) {
        super(client);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return "Le os parametros de usuario persistentes (SPA/GPA, aba 'Parametros' da SU3) via CALL FUNCTION "
            + "'SUSR_USER_PARAMETERS_GET'. Nao existe endpoint REST direto no ADT para isso -- esta tool cria uma "
            + "classe ABAP temporaria que executa essa chamada nativa, roda via classrun e remove a classe ao "
            + "final (deleteHelperAfterRun=true por padrao).";
    }

    @Override
    public JsonObject getInputSchema() {
        JsonObject helperPackageNameProp = new JsonObject();
        helperPackageNameProp.addProperty("type", "string");
        helperPackageNameProp.addProperty("description", "Pacote (DEVCLASS) onde a classe helper temporaria sera criada.");

        JsonObject userNameProp = new JsonObject();
        userNameProp.addProperty("type", "string");
        userNameProp.addProperty("description", "Usuario cujos parametros serao lidos. Default: o usuario logado na conexao ADT atual.");

        JsonObject parameterIdsProp = new JsonObject();
        parameterIdsProp.addProperty("type", "array");
        parameterIdsProp.addProperty("description", "Filtro opcional de PARID's (ex: ['BUK', 'WRK']). Se omitido, retorna todos os parametros do usuario.");
        JsonObject parameterIdsItems = new JsonObject();
        parameterIdsItems.addProperty("type", "string");
        parameterIdsProp.add("items", parameterIdsItems);

        JsonObject withTextProp = new JsonObject();
        withTextProp.addProperty("type", "boolean");
        withTextProp.addProperty("description", "Incluir o texto descritivo (PARTEXT) de cada parametro. Default: true.");

        JsonObject transportProp = new JsonObject();
        transportProp.addProperty("type", "string");
        transportProp.addProperty("description", "Numero do transporte para a classe helper (opcional; geralmente nao necessario em $TMP).");

        JsonObject helperClassNameProp = new JsonObject();
        helperClassNameProp.addProperty("type", "string");
        helperClassNameProp.addProperty("description",
            "Nome da classe helper temporaria (opcional; default 'ZCL_MCP_PGET_' + usuario, truncado a 30 caracteres).");

        JsonObject deleteHelperProp = new JsonObject();
        deleteHelperProp.addProperty("type", "boolean");
        deleteHelperProp.addProperty("description", "Remover a classe helper apos a execucao. Default: true.");

        JsonObject properties = new JsonObject();
        properties.add("helperPackageName", helperPackageNameProp);
        properties.add("userName", userNameProp);
        properties.add("parameterIds", parameterIdsProp);
        properties.add("withText", withTextProp);
        properties.add("transport", transportProp);
        properties.add("helperClassName", helperClassNameProp);
        properties.add("deleteHelperAfterRun", deleteHelperProp);

        JsonArray required = new JsonArray();
        required.add("helperPackageName");

        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.add("properties", properties);
        schema.add("required", required);

        return schema;
    }

    @Override
    public String execute(JsonObject arguments) throws Exception {
        String helperPackageName = optString(arguments, "helperPackageName");
        if (helperPackageName == null || helperPackageName.isEmpty()) {
            throw new IllegalArgumentException("Provide 'helperPackageName'.");
        }
        helperPackageName = helperPackageName.toUpperCase();

        String userName = optString(arguments, "userName");
        if (userName == null || userName.isEmpty()) {
            userName = client.getUsername();
        }
        userName = userName.toUpperCase();

        boolean withText = !arguments.has("withText") || arguments.get("withText").isJsonNull()
            || arguments.get("withText").getAsBoolean();

        List<String> parameterIds = new ArrayList<>();
        if (arguments.has("parameterIds") && arguments.get("parameterIds").isJsonArray()) {
            for (JsonElement el : arguments.getAsJsonArray("parameterIds")) {
                parameterIds.add(el.getAsString());
            }
        }

        String transport = optString(arguments, "transport");

        String helperClassName = optString(arguments, "helperClassName");
        if (helperClassName == null || helperClassName.isEmpty()) {
            helperClassName = AbapHelperClassRunner.truncateName("ZCL_MCP_PGET_" + userName, 30);
        } else {
            helperClassName = AbapHelperClassRunner.truncateName(helperClassName, 30);
        }
        boolean deleteHelperAfterRun = !arguments.has("deleteHelperAfterRun") || arguments.get("deleteHelperAfterRun").isJsonNull()
            || arguments.get("deleteHelperAfterRun").getAsBoolean();

        String abapSource = buildHelperClassSource(helperClassName, userName, withText, parameterIds);

        String runResultJson = AbapHelperClassRunner.run(
            client, helperClassName, helperPackageName, abapSource, transport, deleteHelperAfterRun);
        String consoleOutput = AbapHelperClassRunner.extractConsoleOutput(runResultJson);

        JsonArray parameters = new JsonArray();
        JsonObject summary = new JsonObject();
        for (String rawLine : consoleOutput.split("\\r?\\n")) {
            String line = rawLine.trim();
            if (line.isEmpty()) continue;
            if (line.startsWith("PARAM;")) {
                parameters.add(AbapHelperClassRunner.parseSemicolonFields(line.substring("PARAM;".length())));
            } else if (line.startsWith("MODE=")) {
                summary = AbapHelperClassRunner.parseSemicolonFields(line);
            }
        }

        JsonObject output = new JsonObject();
        output.addProperty("userName", userName);
        output.addProperty("helperClassName", helperClassName);
        output.addProperty("helperDeleted", deleteHelperAfterRun);
        output.add("parameters", parameters);
        output.add("summary", summary);
        output.addProperty("rawOutput", consoleOutput);
        String result = summary.has("RESULT") ? summary.get("RESULT").getAsString() : "";
        output.addProperty("success", "OK".equals(result));

        return output.toString();
    }

    private String buildHelperClassSource(String helperClassName, String userName, boolean withText,
            List<String> parameterIds) {
        String filterLiteral = AbapHelperClassRunner.buildIdTableLiteral(parameterIds);

        return "CLASS " + helperClassName + " DEFINITION\n"
            + "  PUBLIC\n"
            + "  FINAL\n"
            + "  CREATE PUBLIC.\n"
            + "\n"
            + "  PUBLIC SECTION.\n"
            + "    INTERFACES if_oo_adt_classrun.\n"
            + "ENDCLASS.\n"
            + "\n"
            + "CLASS " + helperClassName + " IMPLEMENTATION.\n"
            + "  METHOD if_oo_adt_classrun~main.\n"
            + "    CONSTANTS:\n"
            + "      lc_user      TYPE usr02-bname VALUE " + AbapHelperClassRunner.toAbapLiteral(userName) + ",\n"
            + "      lc_with_text TYPE char01 VALUE " + AbapHelperClassRunner.toAbapLiteral(withText ? "X" : "") + ".\n"
            + "\n"
            + "    DATA:\n"
            + "      lt_parameters TYPE ustyp_t_parameters,\n"
            + "      lt_filter     TYPE STANDARD TABLE OF usr05-parid WITH EMPTY KEY,\n"
            + "      lv_count      TYPE i,\n"
            + "      lv_subrc      TYPE sysubrc,\n"
            + "      lv_result     TYPE string,\n"
            + "      lv_exception  TYPE string.\n"
            + "\n"
            + "    TRY.\n"
            + "        lt_filter = VALUE " + filterLiteral + ".\n"
            + "\n"
            + "        CALL FUNCTION 'SUSR_USER_PARAMETERS_GET'\n"
            + "          EXPORTING\n"
            + "            user_name           = lc_user\n"
            + "            with_text           = lc_with_text\n"
            + "          TABLES\n"
            + "            user_parameters     = lt_parameters\n"
            + "          EXCEPTIONS\n"
            + "            user_name_not_exist = 1\n"
            + "            OTHERS              = 2.\n"
            + "\n"
            + "        lv_subrc = sy-subrc.\n"
            + "\n"
            + "        lv_result = SWITCH string( lv_subrc\n"
            + "          WHEN 0 THEN 'OK'\n"
            + "          WHEN 1 THEN 'USER_NAME_NOT_EXIST'\n"
            + "          ELSE |SUBRC_{ lv_subrc }| ).\n"
            + "\n"
            + "        LOOP AT lt_parameters INTO DATA(ls_parameter).\n"
            + "          IF lt_filter IS NOT INITIAL AND NOT line_exists( lt_filter[ table_line = ls_parameter-parid ] ).\n"
            + "            CONTINUE.\n"
            + "          ENDIF.\n"
            + "          lv_count += 1.\n"
            + "          out->write( |PARAM;PARID={ ls_parameter-parid };PARVA={ ls_parameter-parva };PARTEXT={ ls_parameter-partext }| ).\n"
            + "        ENDLOOP.\n"
            + "\n"
            + "        out->write( |MODE=GET; USER={ lc_user }; RESULT={ lv_result }; SUBRC={ lv_subrc }; COUNT={ lv_count }| ).\n"
            + "      CATCH cx_root INTO DATA(lx_root).\n"
            + "        lv_exception = lx_root->get_text( ).\n"
            + "        out->write( |MODE=GET; USER={ lc_user }; RESULT=EXCEPTION; TEXT={ lv_exception }| ).\n"
            + "    ENDTRY.\n"
            + "  ENDMETHOD.\n"
            + "ENDCLASS.";
    }
}
