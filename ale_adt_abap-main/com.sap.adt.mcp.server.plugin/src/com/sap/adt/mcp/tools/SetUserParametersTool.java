package com.sap.adt.mcp.tools;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.sap.adt.mcp.sap.AdtRestClient;

/**
 * Tool: sap_set_user_parameters -- Create/update a user's persistent parameters (SPA/GPA,
 * the SU3 "Parameters" tab / memory IDs), via CALL FUNCTION 'SUSR_USER_PARAMETERS_GET' (read
 * existing, to merge) followed by 'SUSR_USER_PARAMETERS_PUT'. ADT has no direct REST endpoint
 * for this, so this tool creates a throwaway IF_OO_ADT_CLASSRUN helper class that performs
 * both calls, runs it via classrun, parses the structured console output (which includes the
 * post-write values for verification), and (by default) deletes the helper class afterwards.
 */
public class SetUserParametersTool extends AbstractMcpTool {

    public static final String NAME = "sap_set_user_parameters";

    public SetUserParametersTool(AdtRestClient client) {
        super(client);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return "Cria/atualiza parametros de usuario persistentes (SPA/GPA, aba 'Parametros' da SU3) via CALL "
            + "FUNCTION 'SUSR_USER_PARAMETERS_GET' (le os existentes, para mesclar) seguido de "
            + "'SUSR_USER_PARAMETERS_PUT'. Nao existe endpoint REST direto no ADT para isso -- esta tool cria uma "
            + "classe ABAP temporaria que executa essas chamadas nativas, roda via classrun, retorna os valores "
            + "pos-gravacao para verificacao, e remove a classe ao final (deleteHelperAfterRun=true por padrao).";
    }

    @Override
    public JsonObject getInputSchema() {
        JsonObject helperPackageNameProp = new JsonObject();
        helperPackageNameProp.addProperty("type", "string");
        helperPackageNameProp.addProperty("description", "Pacote (DEVCLASS) onde a classe helper temporaria sera criada.");

        JsonObject userNameProp = new JsonObject();
        userNameProp.addProperty("type", "string");
        userNameProp.addProperty("description", "Usuario cujos parametros serao alterados. Default: o usuario logado na conexao ADT atual.");

        JsonObject parameterIdProp = new JsonObject();
        parameterIdProp.addProperty("type", "string");
        parameterIdProp.addProperty("description", "PARID do parametro (ex: 'BUK').");
        JsonObject parameterValueProp = new JsonObject();
        parameterValueProp.addProperty("type", "string");
        parameterValueProp.addProperty("description", "Novo valor (PARVA) do parametro.");
        JsonObject parameterTextProp = new JsonObject();
        parameterTextProp.addProperty("type", "string");
        parameterTextProp.addProperty("description", "Texto descritivo (PARTEXT) opcional; se omitido, mantem o texto existente quando o parametro ja existir.");
        JsonObject parameterItemProperties = new JsonObject();
        parameterItemProperties.add("parameterId", parameterIdProp);
        parameterItemProperties.add("value", parameterValueProp);
        parameterItemProperties.add("text", parameterTextProp);
        JsonArray parameterItemRequired = new JsonArray();
        parameterItemRequired.add("parameterId");
        parameterItemRequired.add("value");
        JsonObject parameterItemSchema = new JsonObject();
        parameterItemSchema.addProperty("type", "object");
        parameterItemSchema.add("properties", parameterItemProperties);
        parameterItemSchema.add("required", parameterItemRequired);

        JsonObject parametersProp = new JsonObject();
        parametersProp.addProperty("type", "array");
        parametersProp.addProperty("description", "Lista de parametros a definir, ex: [{\"parameterId\":\"BUK\",\"value\":\"1000\"}].");
        parametersProp.add("items", parameterItemSchema);

        JsonObject transportProp = new JsonObject();
        transportProp.addProperty("type", "string");
        transportProp.addProperty("description", "Numero do transporte para a classe helper (opcional; geralmente nao necessario em $TMP).");

        JsonObject helperClassNameProp = new JsonObject();
        helperClassNameProp.addProperty("type", "string");
        helperClassNameProp.addProperty("description",
            "Nome da classe helper temporaria (opcional; default 'ZCL_MCP_PSET_' + usuario, truncado a 30 caracteres).");

        JsonObject deleteHelperProp = new JsonObject();
        deleteHelperProp.addProperty("type", "boolean");
        deleteHelperProp.addProperty("description", "Remover a classe helper apos a execucao. Default: true.");

        JsonObject properties = new JsonObject();
        properties.add("helperPackageName", helperPackageNameProp);
        properties.add("userName", userNameProp);
        properties.add("parameters", parametersProp);
        properties.add("transport", transportProp);
        properties.add("helperClassName", helperClassNameProp);
        properties.add("deleteHelperAfterRun", deleteHelperProp);

        JsonArray required = new JsonArray();
        required.add("helperPackageName");
        required.add("parameters");

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

        if (!arguments.has("parameters") || !arguments.get("parameters").isJsonArray()
                || arguments.getAsJsonArray("parameters").size() == 0) {
            throw new IllegalArgumentException("Provide 'parameters': a list of {parameterId, value, [text]}.");
        }

        List<String[]> parameters = new ArrayList<>();
        for (JsonElement el : arguments.getAsJsonArray("parameters")) {
            JsonObject entry = el.getAsJsonObject();
            if (!entry.has("parameterId") || !entry.has("value")) {
                throw new IllegalArgumentException("Each entry in 'parameters' needs 'parameterId' and 'value'.");
            }
            String parameterId = entry.get("parameterId").getAsString();
            String value = entry.get("value").getAsString();
            String text = entry.has("text") && !entry.get("text").isJsonNull() ? entry.get("text").getAsString() : "";
            parameters.add(new String[] { parameterId, value, text });
        }

        String transport = optString(arguments, "transport");

        String helperClassName = optString(arguments, "helperClassName");
        if (helperClassName == null || helperClassName.isEmpty()) {
            helperClassName = AbapHelperClassRunner.truncateName("ZCL_MCP_PSET_" + userName, 30);
        } else {
            helperClassName = AbapHelperClassRunner.truncateName(helperClassName, 30);
        }
        boolean deleteHelperAfterRun = !arguments.has("deleteHelperAfterRun") || arguments.get("deleteHelperAfterRun").isJsonNull()
            || arguments.get("deleteHelperAfterRun").getAsBoolean();

        String abapSource = buildHelperClassSource(helperClassName, userName, parameters);

        String runResultJson = AbapHelperClassRunner.run(
            client, helperClassName, helperPackageName, abapSource, transport, deleteHelperAfterRun);
        String consoleOutput = AbapHelperClassRunner.extractConsoleOutput(runResultJson);

        JsonArray resultingParameters = new JsonArray();
        JsonObject summary = new JsonObject();
        for (String rawLine : consoleOutput.split("\\r?\\n")) {
            String line = rawLine.trim();
            if (line.isEmpty()) continue;
            if (line.startsWith("PARAM;")) {
                resultingParameters.add(AbapHelperClassRunner.parseSemicolonFields(line.substring("PARAM;".length())));
            } else if (line.startsWith("MODE=")) {
                summary = AbapHelperClassRunner.parseSemicolonFields(line);
            }
        }

        JsonObject output = new JsonObject();
        output.addProperty("userName", userName);
        output.addProperty("helperClassName", helperClassName);
        output.addProperty("helperDeleted", deleteHelperAfterRun);
        output.add("parameters", resultingParameters);
        output.add("summary", summary);
        output.addProperty("rawOutput", consoleOutput);
        String result = summary.has("RESULT") ? summary.get("RESULT").getAsString() : "";
        output.addProperty("success", "OK".equals(result));

        return output.toString();
    }

    private String buildHelperClassSource(String helperClassName, String userName, List<String[]> parameters) {
        StringBuilder inputEntries = new StringBuilder();
        List<String> ids = new ArrayList<>();
        for (String[] p : parameters) {
            String parameterId = p[0].toUpperCase();
            ids.add(parameterId);
            inputEntries.append("( parid = ").append(AbapHelperClassRunner.toAbapLiteral(parameterId))
                .append(" parva = ").append(AbapHelperClassRunner.toAbapLiteral(p[1]))
                .append(" partext = ").append(AbapHelperClassRunner.toAbapLiteral(p[2]))
                .append(" ) ");
        }
        String inputLiteral = "#( " + inputEntries.toString().trim() + " )";
        String filterLiteral = AbapHelperClassRunner.buildIdTableLiteral(ids);

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
            + "    CONSTANTS lc_user TYPE usr02-bname VALUE " + AbapHelperClassRunner.toAbapLiteral(userName) + ".\n"
            + "\n"
            + "    DATA:\n"
            + "      lt_before     TYPE ustyp_t_parameters,\n"
            + "      lt_after      TYPE ustyp_t_parameters,\n"
            + "      lt_verify     TYPE ustyp_t_parameters,\n"
            + "      lt_input      TYPE ustyp_t_parameters,\n"
            + "      lt_filter     TYPE STANDARD TABLE OF usr05-parid WITH EMPTY KEY,\n"
            + "      lv_get_subrc  TYPE sysubrc,\n"
            + "      lv_put_subrc  TYPE sysubrc,\n"
            + "      lv_result     TYPE string,\n"
            + "      lv_exception  TYPE string.\n"
            + "\n"
            + "    TRY.\n"
            + "        lt_input = VALUE " + inputLiteral + ".\n"
            + "        lt_filter = VALUE " + filterLiteral + ".\n"
            + "\n"
            + "        CALL FUNCTION 'SUSR_USER_PARAMETERS_GET'\n"
            + "          EXPORTING\n"
            + "            user_name           = lc_user\n"
            + "            with_text           = 'X'\n"
            + "          TABLES\n"
            + "            user_parameters     = lt_before\n"
            + "          EXCEPTIONS\n"
            + "            user_name_not_exist = 1\n"
            + "            OTHERS              = 2.\n"
            + "\n"
            + "        lv_get_subrc = sy-subrc.\n"
            + "\n"
            + "        IF lv_get_subrc <> 0.\n"
            + "          lv_result = SWITCH string( lv_get_subrc\n"
            + "            WHEN 1 THEN 'USER_NAME_NOT_EXIST'\n"
            + "            ELSE |SUBRC_{ lv_get_subrc }| ).\n"
            + "          out->write( |MODE=SET; USER={ lc_user }; RESULT={ lv_result }; SUBRC={ lv_get_subrc }; BEFORE_COUNT={ lines( lt_before ) }; AFTER_COUNT={ lines( lt_before ) }| ).\n"
            + "          RETURN.\n"
            + "        ENDIF.\n"
            + "\n"
            + "        lt_after = lt_before.\n"
            + "\n"
            + "        LOOP AT lt_input INTO DATA(ls_input).\n"
            + "          READ TABLE lt_after WITH KEY parid = ls_input-parid ASSIGNING FIELD-SYMBOL(<ls_after>).\n"
            + "          IF sy-subrc = 0.\n"
            + "            <ls_after>-parva = ls_input-parva.\n"
            + "            IF ls_input-partext IS NOT INITIAL.\n"
            + "              <ls_after>-partext = ls_input-partext.\n"
            + "            ENDIF.\n"
            + "          ELSE.\n"
            + "            APPEND ls_input TO lt_after.\n"
            + "          ENDIF.\n"
            + "        ENDLOOP.\n"
            + "\n"
            + "        CALL FUNCTION 'SUSR_USER_PARAMETERS_PUT'\n"
            + "          EXPORTING\n"
            + "            user_name           = lc_user\n"
            + "          TABLES\n"
            + "            user_parameters     = lt_after\n"
            + "          EXCEPTIONS\n"
            + "            user_name_not_exist = 1\n"
            + "            OTHERS              = 2.\n"
            + "\n"
            + "        lv_put_subrc = sy-subrc.\n"
            + "\n"
            + "        lv_result = SWITCH string( lv_put_subrc\n"
            + "          WHEN 0 THEN 'OK'\n"
            + "          WHEN 1 THEN 'USER_NAME_NOT_EXIST'\n"
            + "          ELSE |SUBRC_{ lv_put_subrc }| ).\n"
            + "\n"
            + "        CALL FUNCTION 'SUSR_USER_PARAMETERS_GET'\n"
            + "          EXPORTING\n"
            + "            user_name           = lc_user\n"
            + "            with_text           = 'X'\n"
            + "          TABLES\n"
            + "            user_parameters     = lt_verify\n"
            + "          EXCEPTIONS\n"
            + "            user_name_not_exist = 1\n"
            + "            OTHERS              = 2.\n"
            + "\n"
            + "        LOOP AT lt_verify INTO DATA(ls_verify).\n"
            + "          IF lt_filter IS NOT INITIAL AND NOT line_exists( lt_filter[ table_line = ls_verify-parid ] ).\n"
            + "            CONTINUE.\n"
            + "          ENDIF.\n"
            + "          out->write( |PARAM;PARID={ ls_verify-parid };PARVA={ ls_verify-parva };PARTEXT={ ls_verify-partext }| ).\n"
            + "        ENDLOOP.\n"
            + "\n"
            + "        out->write( |MODE=SET; USER={ lc_user }; RESULT={ lv_result }; SUBRC={ lv_put_subrc }; BEFORE_COUNT={ lines( lt_before ) }; AFTER_COUNT={ lines( lt_after ) }| ).\n"
            + "      CATCH cx_root INTO DATA(lx_root).\n"
            + "        lv_exception = lx_root->get_text( ).\n"
            + "        out->write( |MODE=SET; USER={ lc_user }; RESULT=EXCEPTION; TEXT={ lv_exception }| ).\n"
            + "    ENDTRY.\n"
            + "  ENDMETHOD.\n"
            + "ENDCLASS.";
    }
}
