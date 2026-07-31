package com.sap.adt.mcp.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sap.adt.mcp.sap.AdtRestClient;

/**
 * Tool: sap_create_transaction -- Create a classic transaction code (TSTC/TSTCT) bound to a
 * report program, via CALL FUNCTION 'RPY_TRANSACTION_INSERT' (the same function module the
 * SE93 transaction calls internally). ADT has no direct REST endpoint for this, so this tool
 * creates a throwaway IF_OO_ADT_CLASSRUN helper class that performs the CALL FUNCTION, runs it
 * via classrun, parses the structured console output, and (by default) deletes the helper
 * class afterwards. Ported from the joamel01/SAP_MCP reference project's verified template.
 *
 * Note: RPY_TRANSACTION_INSERT's exact parameter signature is known to be stable in current
 * ECC/S4HANA releases, but has not been tested against this specific system -- if it fails
 * with a parameter-not-found dump, the helper class source (returned in the response on
 * failure, since deleteHelperAfterRun defaults to leaving it for inspection on error) shows
 * exactly what was attempted.
 */
public class CreateTransactionTool extends AbstractMcpTool {

    public static final String NAME = "sap_create_transaction";

    public CreateTransactionTool(AdtRestClient client) {
        super(client);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return "Cria um codigo de transacao (T-code) classico vinculado a um programa, via CALL FUNCTION "
            + "'RPY_TRANSACTION_INSERT' (a mesma function module que a SE93 usa internamente). Nao existe "
            + "endpoint REST direto no ADT para isso -- esta tool cria uma classe ABAP temporaria que executa "
            + "essa chamada nativa, roda via classrun e remove a classe ao final (deleteHelperAfterRun=true por "
            + "padrao). Requer um pacote (packageName) para a classe temporaria E como development_class da "
            + "transacao criada.";
    }

    @Override
    public JsonObject getInputSchema() {
        JsonObject transactionCodeProp = new JsonObject();
        transactionCodeProp.addProperty("type", "string");
        transactionCodeProp.addProperty("description", "Codigo de transacao a criar (ex: 'ZMY_TX').");

        JsonObject programNameProp = new JsonObject();
        programNameProp.addProperty("type", "string");
        programNameProp.addProperty("description", "Programa ABAP (REPORT executavel) que a transacao deve chamar.");

        JsonObject shortTextProp = new JsonObject();
        shortTextProp.addProperty("type", "string");
        shortTextProp.addProperty("description", "Texto curto/descricao da transacao (max. 70 caracteres).");

        JsonObject packageNameProp = new JsonObject();
        packageNameProp.addProperty("type", "string");
        packageNameProp.addProperty("description",
            "Pacote (DEVCLASS) usado tanto para a classe helper temporaria quanto como development_class da "
            + "transacao criada.");

        JsonObject variantProp = new JsonObject();
        variantProp.addProperty("type", "string");
        variantProp.addProperty("description", "Variante de tela inicial opcional (max. 14 caracteres).");

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
        properties.add("programName", programNameProp);
        properties.add("shortText", shortTextProp);
        properties.add("packageName", packageNameProp);
        properties.add("variant", variantProp);
        properties.add("transport", transportProp);
        properties.add("helperClassName", helperClassNameProp);
        properties.add("deleteHelperAfterRun", deleteHelperProp);

        JsonArray required = new JsonArray();
        required.add("transactionCode");
        required.add("programName");
        required.add("shortText");
        required.add("packageName");

        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.add("properties", properties);
        schema.add("required", required);

        return schema;
    }

    @Override
    public String execute(JsonObject arguments) throws Exception {
        String transactionCode = optString(arguments, "transactionCode");
        String programName = optString(arguments, "programName");
        String shortText = optString(arguments, "shortText");
        String packageName = optString(arguments, "packageName");
        if (transactionCode == null || transactionCode.isEmpty() || programName == null || programName.isEmpty()
                || shortText == null || shortText.isEmpty() || packageName == null || packageName.isEmpty()) {
            throw new IllegalArgumentException("Provide 'transactionCode', 'programName', 'shortText' and 'packageName'.");
        }
        transactionCode = transactionCode.toUpperCase();
        programName = programName.toUpperCase();
        packageName = packageName.toUpperCase();
        if (shortText.length() > 70) shortText = shortText.substring(0, 70);

        String variant = optString(arguments, "variant");
        String transport = optString(arguments, "transport");

        String helperClassName = optString(arguments, "helperClassName");
        if (helperClassName == null || helperClassName.isEmpty()) {
            helperClassName = AbapHelperClassRunner.truncateName("ZCL_MCP_TX_" + transactionCode, 30);
        } else {
            helperClassName = AbapHelperClassRunner.truncateName(helperClassName, 30);
        }
        boolean deleteHelperAfterRun = !arguments.has("deleteHelperAfterRun") || arguments.get("deleteHelperAfterRun").isJsonNull()
            || arguments.get("deleteHelperAfterRun").getAsBoolean();

        String abapSource = buildHelperClassSource(helperClassName, transactionCode, programName, shortText,
            packageName, transport, variant);

        String runResultJson = AbapHelperClassRunner.run(
            client, helperClassName, packageName, abapSource, transport, deleteHelperAfterRun);
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

    private String buildHelperClassSource(String helperClassName, String transactionCode, String programName,
            String shortText, String packageName, String transport, String variant) {
        String variantConstant = (variant != null && !variant.isEmpty())
            ? ",\n      lc_variant     TYPE c LENGTH 14 VALUE " + AbapHelperClassRunner.toAbapLiteral(variant)
            : "";
        String variantParameter = (variant != null && !variant.isEmpty())
            ? "\n            variant                    = lc_variant"
            : "";

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
            + "      lc_program     TYPE syrepid VALUE " + AbapHelperClassRunner.toAbapLiteral(programName) + ",\n"
            + "      lc_shorttext   TYPE tstct-ttext VALUE " + AbapHelperClassRunner.toAbapLiteral(shortText) + ",\n"
            + "      lc_devclass    TYPE tadir-devclass VALUE " + AbapHelperClassRunner.toAbapLiteral(packageName) + ",\n"
            + "      lc_trkorr      TYPE trkorr VALUE " + AbapHelperClassRunner.toAbapLiteral(transport == null ? "" : transport)
            + variantConstant + ".\n"
            + "\n"
            + "    DATA:\n"
            + "      lv_subrc       TYPE sysubrc,\n"
            + "      lv_result      TYPE string,\n"
            + "      lv_exists_flag TYPE c LENGTH 1,\n"
            + "      lv_exception   TYPE string.\n"
            + "\n"
            + "    TRY.\n"
            + "        CALL FUNCTION 'RPY_TRANSACTION_INSERT'\n"
            + "          EXPORTING\n"
            + "            program                    = lc_program" + variantParameter + "\n"
            + "            transaction                = lc_transaction\n"
            + "            shorttext                  = lc_shorttext\n"
            + "            language                   = sy-langu\n"
            + "            development_class          = lc_devclass\n"
            + "            transport_number           = lc_trkorr\n"
            + "            transaction_type           = 'R'\n"
            + "            suppress_corr_check        = 'X'\n"
            + "            suppress_corr_insert       = 'X'\n"
            + "          EXCEPTIONS\n"
            + "            cancelled                  = 1\n"
            + "            already_exist              = 2\n"
            + "            permission_error           = 3\n"
            + "            name_not_allowed           = 4\n"
            + "            name_conflict              = 5\n"
            + "            illegal_type               = 6\n"
            + "            object_inconsistent        = 7\n"
            + "            db_access_error            = 8\n"
            + "            OTHERS                     = 9.\n"
            + "\n"
            + "        lv_subrc = sy-subrc.\n"
            + "        lv_result = SWITCH string( lv_subrc\n"
            + "          WHEN 0 THEN 'OK'\n"
            + "          WHEN 1 THEN 'CANCELLED'\n"
            + "          WHEN 2 THEN 'ALREADY_EXIST'\n"
            + "          WHEN 3 THEN 'PERMISSION_ERROR'\n"
            + "          WHEN 4 THEN 'NAME_NOT_ALLOWED'\n"
            + "          WHEN 5 THEN 'NAME_CONFLICT'\n"
            + "          WHEN 6 THEN 'ILLEGAL_TYPE'\n"
            + "          WHEN 7 THEN 'OBJECT_INCONSISTENT'\n"
            + "          WHEN 8 THEN 'DB_ACCESS_ERROR'\n"
            + "          ELSE |SUBRC_{ lv_subrc }| ).\n"
            + "\n"
            + "        SELECT SINGLE @abap_true\n"
            + "          FROM tstc\n"
            + "          WHERE tcode = @lc_transaction\n"
            + "          INTO @DATA(lv_exists).\n"
            + "\n"
            + "        lv_exists_flag = COND #( WHEN sy-subrc = 0 THEN '1' ELSE '0' ).\n"
            + "        out->write( |MODE=CREATE; TCODE={ lc_transaction }; RESULT={ lv_result }; SUBRC={ lv_subrc }; EXISTS={ lv_exists_flag }| ).\n"
            + "      CATCH cx_root INTO DATA(lx_root).\n"
            + "        lv_exception = lx_root->get_text( ).\n"
            + "        out->write( |MODE=CREATE; TCODE={ lc_transaction }; RESULT=EXCEPTION; TEXT={ lv_exception }| ).\n"
            + "    ENDTRY.\n"
            + "  ENDMETHOD.\n"
            + "ENDCLASS.";
    }
}
