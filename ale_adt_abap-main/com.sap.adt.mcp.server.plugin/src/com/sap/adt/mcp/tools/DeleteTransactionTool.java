package com.sap.adt.mcp.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sap.adt.mcp.sap.AdtRestClient;

/**
 * Tool: sap_delete_transaction -- Delete a classic transaction code (TSTC/TSTCT), via
 * CALL FUNCTION 'RPY_TRANSACTION_DELETE' (the same function module SE93 calls internally).
 * Same throwaway-helper-class technique as sap_create_transaction, since ADT has no direct
 * REST endpoint for this.
 */
public class DeleteTransactionTool extends AbstractMcpTool {

    public static final String NAME = "sap_delete_transaction";

    public DeleteTransactionTool(AdtRestClient client) {
        super(client);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return "Deleta um codigo de transacao (T-code) classico, via CALL FUNCTION 'RPY_TRANSACTION_DELETE' (a "
            + "mesma function module que a SE93 usa internamente). Mesma tecnica de classe helper temporaria de "
            + "sap_create_transaction, ja que nao ha endpoint REST direto no ADT para isso.";
    }

    @Override
    public JsonObject getInputSchema() {
        JsonObject transactionCodeProp = new JsonObject();
        transactionCodeProp.addProperty("type", "string");
        transactionCodeProp.addProperty("description", "Codigo de transacao a deletar (ex: 'ZMY_TX').");

        JsonObject helperPackageNameProp = new JsonObject();
        helperPackageNameProp.addProperty("type", "string");
        helperPackageNameProp.addProperty("description", "Pacote (DEVCLASS) onde a classe helper temporaria sera criada.");

        JsonObject transportProp = new JsonObject();
        transportProp.addProperty("type", "string");
        transportProp.addProperty("description", "Numero do transporte (opcional).");

        JsonObject helperClassNameProp = new JsonObject();
        helperClassNameProp.addProperty("type", "string");
        helperClassNameProp.addProperty("description",
            "Nome da classe helper temporaria (opcional; default 'ZCL_MCP_TX_' + transactionCode, truncado a 30 caracteres).");

        JsonObject deleteHelperProp = new JsonObject();
        deleteHelperProp.addProperty("type", "boolean");
        deleteHelperProp.addProperty("description", "Remover a classe helper apos a execucao. Default: true.");

        JsonObject properties = new JsonObject();
        properties.add("transactionCode", transactionCodeProp);
        properties.add("helperPackageName", helperPackageNameProp);
        properties.add("transport", transportProp);
        properties.add("helperClassName", helperClassNameProp);
        properties.add("deleteHelperAfterRun", deleteHelperProp);

        JsonArray required = new JsonArray();
        required.add("transactionCode");
        required.add("helperPackageName");

        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.add("properties", properties);
        schema.add("required", required);

        return schema;
    }

    @Override
    public String execute(JsonObject arguments) throws Exception {
        String transactionCode = optString(arguments, "transactionCode");
        String helperPackageName = optString(arguments, "helperPackageName");
        if (transactionCode == null || transactionCode.isEmpty()
                || helperPackageName == null || helperPackageName.isEmpty()) {
            throw new IllegalArgumentException("Provide 'transactionCode' and 'helperPackageName'.");
        }
        transactionCode = transactionCode.toUpperCase();
        helperPackageName = helperPackageName.toUpperCase();

        String transport = optString(arguments, "transport");

        String helperClassName = optString(arguments, "helperClassName");
        if (helperClassName == null || helperClassName.isEmpty()) {
            helperClassName = AbapHelperClassRunner.truncateName("ZCL_MCP_TX_" + transactionCode, 30);
        } else {
            helperClassName = AbapHelperClassRunner.truncateName(helperClassName, 30);
        }
        boolean deleteHelperAfterRun = !arguments.has("deleteHelperAfterRun") || arguments.get("deleteHelperAfterRun").isJsonNull()
            || arguments.get("deleteHelperAfterRun").getAsBoolean();

        String abapSource = buildHelperClassSource(helperClassName, transactionCode, transport);

        String runResultJson = AbapHelperClassRunner.run(
            client, helperClassName, helperPackageName, abapSource, transport, deleteHelperAfterRun);
        String consoleOutput = AbapHelperClassRunner.extractConsoleOutput(runResultJson);
        JsonObject parsed = AbapHelperClassRunner.parseSemicolonFields(consoleOutput.trim());

        JsonObject output = new JsonObject();
        output.addProperty("transactionCode", transactionCode);
        output.addProperty("helperClassName", helperClassName);
        output.addProperty("helperDeleted", deleteHelperAfterRun);
        output.addProperty("rawOutput", consoleOutput);
        output.add("result", parsed);
        String result = parsed.has("RESULT") ? parsed.get("RESULT").getAsString() : "";
        output.addProperty("success", "OK".equals(result));

        return output.toString();
    }

    private String buildHelperClassSource(String helperClassName, String transactionCode, String transport) {
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
            + "      lc_transaction TYPE tcode VALUE " + AbapHelperClassRunner.toAbapLiteral(transactionCode) + ",\n"
            + "      lc_trkorr      TYPE trkorr VALUE " + AbapHelperClassRunner.toAbapLiteral(transport == null ? "" : transport) + ".\n"
            + "\n"
            + "    DATA:\n"
            + "      lv_subrc       TYPE sysubrc,\n"
            + "      lv_result      TYPE string,\n"
            + "      lv_exists_flag TYPE c LENGTH 1,\n"
            + "      lv_exception   TYPE string.\n"
            + "\n"
            + "    TRY.\n"
            + "        CALL FUNCTION 'RPY_TRANSACTION_DELETE'\n"
            + "          EXPORTING\n"
            + "            transaction                = lc_transaction\n"
            + "            transport_number           = lc_trkorr\n"
            + "            suppress_authority_check   = 'X'\n"
            + "            suppress_corr_insert       = 'X'\n"
            + "            suppress_corr_check        = 'X'\n"
            + "          EXCEPTIONS\n"
            + "            not_excecuted              = 1\n"
            + "            object_not_found           = 2\n"
            + "            OTHERS                     = 3.\n"
            + "\n"
            + "        lv_subrc = sy-subrc.\n"
            + "        lv_result = SWITCH string( lv_subrc\n"
            + "          WHEN 0 THEN 'OK'\n"
            + "          WHEN 1 THEN 'NOT_EXCECUTED'\n"
            + "          WHEN 2 THEN 'OBJECT_NOT_FOUND'\n"
            + "          ELSE |SUBRC_{ lv_subrc }| ).\n"
            + "\n"
            + "        SELECT SINGLE @abap_true\n"
            + "          FROM tstc\n"
            + "          WHERE tcode = @lc_transaction\n"
            + "          INTO @DATA(lv_exists).\n"
            + "\n"
            + "        lv_exists_flag = COND #( WHEN sy-subrc = 0 THEN '1' ELSE '0' ).\n"
            + "        out->write( |MODE=DELETE; TCODE={ lc_transaction }; RESULT={ lv_result }; SUBRC={ lv_subrc }; EXISTS={ lv_exists_flag }| ).\n"
            + "      CATCH cx_root INTO DATA(lx_root).\n"
            + "        lv_exception = lx_root->get_text( ).\n"
            + "        out->write( |MODE=DELETE; TCODE={ lc_transaction }; RESULT=EXCEPTION; TEXT={ lv_exception }| ).\n"
            + "    ENDTRY.\n"
            + "  ENDMETHOD.\n"
            + "ENDCLASS.";
    }
}
