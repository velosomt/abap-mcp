package com.sap.adt.mcp.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sap.adt.mcp.sap.AdtRestClient;

/**
 * Tool: sap_search_repository -- Busca no diretório de objetos do repositório (tabela
 * TADIR) por pacote, autor, tipo e/ou nome. Resolve duas lacunas do sap_search_object
 * (que só faz quick-search por nome/tipo, global) e do sap_get_package_tree (que só
 * devolve o 1º nível de nós, não os objetos): aqui, passando packageName, você obtém
 * TODOS os objetos contidos no pacote; e qualquer combinação de filtros funciona.
 *
 * <p>Estritamente read-only: monta um SELECT em TADIR e delega ao mesmo endpoint de
 * Data Preview usado por sap_sql_query (POST /sap/bc/adt/datapreview/freestyle). Não
 * cria, altera nem bloqueia nada.</p>
 */
public class SearchRepositoryTool extends AbstractMcpTool {

    public static final String NAME = "sap_search_repository";

    public SearchRepositoryTool(AdtRestClient client) {
        super(client);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return "Busca objetos no diretório do repositório (tabela TADIR) por pacote, autor, tipo e/ou nome — "
                + "qualquer combinação (ao menos um filtro). Para LISTAR TODOS OS OBJETOS DE UM PACOTE, passe "
                + "packageName (e nada mais). Diferente de sap_search_object (quick-search global por nome) e de "
                + "sap_get_package_tree (só o 1º nível de nós). Read-only (SELECT via Data Preview).";
    }

    @Override
    public JsonObject getInputSchema() {
        JsonObject properties = new JsonObject();

        JsonObject pkgProp = new JsonObject();
        pkgProp.addProperty("type", "string");
        pkgProp.addProperty("description",
                "Pacote (TADIR-DEVCLASS). Para listar tudo de um pacote, passe só isto. Ex.: 'ZSD_APPAVDEV'.");
        properties.add("packageName", pkgProp);

        JsonObject authorProp = new JsonObject();
        authorProp.addProperty("type", "string");
        authorProp.addProperty("description", "Autor/responsável do objeto (TADIR-AUTHOR), ex.: 'ALEXANDRE'.");
        properties.add("author", authorProp);

        JsonObject typeProp = new JsonObject();
        typeProp.addProperty("type", "string");
        typeProp.addProperty("description",
                "Tipo TADIR de 4 letras (R3TR OBJECT), ex.: CLAS, PROG, INTF, FUGR, TABL, DTEL, DOMA, DDLS, "
                + "DDLX, DCLS, BDEF, SRVD, SRVB, MSAG. Aceita também a forma composta (ex.: 'CLAS/OC') — só o "
                + "trecho antes da '/' é usado.");
        properties.add("objectType", typeProp);

        JsonObject nameProp = new JsonObject();
        nameProp.addProperty("type", "string");
        nameProp.addProperty("description",
                "Padrão de nome (TADIR-OBJ_NAME). Use '*' e '?' como coringas (ex.: 'ZCL_SD_*'). "
                + "'_' e '%' literais são tratados como texto, não como coringa.");
        properties.add("namePattern", nameProp);

        JsonObject maxProp = new JsonObject();
        maxProp.addProperty("type", "integer");
        maxProp.addProperty("description", "Máximo de linhas (default 200).");
        properties.add("max", maxProp);

        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.add("properties", properties);
        return schema;
    }

    @Override
    public String execute(JsonObject arguments) throws Exception {
        String packageName = optString(arguments, "packageName");
        String author = optString(arguments, "author");
        String objectType = optString(arguments, "objectType");
        String namePattern = optString(arguments, "namePattern");
        int max = optInt(arguments, "max", 200);

        boolean hasFilter = isSet(packageName) || isSet(author) || isSet(objectType) || isSet(namePattern);
        if (!hasFilter) {
            throw new IllegalArgumentException(
                    "Informe ao menos um filtro: packageName, author, objectType ou namePattern.");
        }

        StringBuilder sql = new StringBuilder(
                "SELECT pgmid, object, obj_name, devclass, author FROM tadir "
                + "WHERE pgmid = 'R3TR' AND delflag <> 'X'");

        if (isSet(packageName)) {
            sql.append(" AND devclass = '").append(sqlLiteral(packageName)).append("'");
        }
        if (isSet(author)) {
            sql.append(" AND author = '").append(sqlLiteral(author)).append("'");
        }
        if (isSet(objectType)) {
            sql.append(" AND object = '").append(sqlLiteral(normalizeType(objectType))).append("'");
        }
        if (isSet(namePattern)) {
            sql.append(" AND obj_name LIKE '").append(wildcardToLike(namePattern.toUpperCase()))
               .append("' ESCAPE '#'");
        }
        sql.append(" ORDER BY object, obj_name");

        JsonObject queryArgs = new JsonObject();
        queryArgs.addProperty("query", sql.toString());
        queryArgs.addProperty("maxRows", max);

        String result = new SqlQueryTool(client).execute(queryArgs);

        JsonObject out = new JsonObject();
        out.addProperty("source", "TADIR");
        out.addProperty("sql", sql.toString());
        out.add("result", JsonParser.parseString(result));
        return out.toString();
    }

    // ---------------------------------------------------------------- helpers

    private boolean isSet(String s) {
        return s != null && !s.trim().isEmpty();
    }

    /** Escapa aspas simples e normaliza para maiúsculas (valores de chave no SAP são uppercase). */
    static String sqlLiteral(String value) {
        return value.trim().toUpperCase().replace("'", "''");
    }

    /** 'CLAS/OC' -> 'CLAS'; 'clas' -> 'CLAS'. */
    static String normalizeType(String type) {
        String t = type.trim().toUpperCase();
        int slash = t.indexOf('/');
        if (slash > 0) {
            t = t.substring(0, slash);
        }
        return t;
    }

    /**
     * Converte coringas do usuário ('*','?') para LIKE ('%','_') e escapa os '%','_','#'
     * literais com '#'. Também duplica aspas simples. Use com ESCAPE '#' na cláusula LIKE.
     */
    static String wildcardToLike(String pattern) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < pattern.length(); i++) {
            char c = pattern.charAt(i);
            switch (c) {
                case '*': sb.append('%'); break;
                case '?': sb.append('_'); break;
                case '%':
                case '_':
                case '#': sb.append('#').append(c); break;
                case '\'': sb.append("''"); break;
                default: sb.append(c);
            }
        }
        return sb.toString();
    }
}
