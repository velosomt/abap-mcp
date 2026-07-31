package com.sap.adt.mcp.tools;

import java.net.http.HttpResponse;

import com.google.gson.JsonObject;
import com.sap.adt.mcp.sap.AdtRestClient;
import com.sap.adt.mcp.sap.AdtXmlParser;

/**
 * Tool: sap_abapgit_list_repos -- List the abapGit repositories linked to packages in
 * this system (read-only). Deliberately does not expose pull/push/create/unlink: those
 * mutate or remove a Git linkage without a prior review step, so they were left out of
 * this port -- only the informational listing and per-repo stage diff were added.
 *
 * Ported from the abap-adt-api reference client (src/api/abapgit.ts): GET
 * /sap/bc/adt/abapgit/repos.
 */
public class AbapGitListReposTool extends AbstractMcpTool {

    public static final String NAME = "sap_abapgit_list_repos";

    public AbapGitListReposTool(AdtRestClient client) {
        super(client);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return "List abapGit repositories linked to packages in this system (read-only: key, package, "
                + "remote URL, branch, sync status). Use sap_abapgit_repo_status for the staged/unstaged diff "
                + "of a specific repo.";
    }

    @Override
    public JsonObject getInputSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.add("properties", new JsonObject());
        return schema;
    }

    @Override
    public String execute(JsonObject arguments) throws Exception {
        HttpResponse<String> response;
        try {
            response = client.get("/sap/bc/adt/abapgit/repos", "application/abapgit.adt.repos.v2+xml");
        } catch (Exception e) {
            String msg = e.getMessage() == null ? "" : e.getMessage();
            if (msg.contains("404") || msg.toLowerCase().contains("resourcenotfound")) {
                // This endpoint is NOT part of standard ADT: it is served by the separate
                // "abapGit ADT Backend" repository (github.com/abapGit/ADT_Backend, itself
                // marked DEPRECATED/NOT WORKING upstream), which must be imported into the
                // system via abapGit and activated. A 404 here means that component simply
                // isn't installed/active on this backend -- not a bug in this tool's request.
                JsonObject output = new JsonObject();
                output.addProperty("status", "unavailable");
                output.addProperty("reason",
                        "O endpoint /sap/bc/adt/abapgit/repos nao existe neste backend. Ele e fornecido por um "
                        + "componente separado (repositorio abapGit/ADT_Backend no GitHub, marcado como "
                        + "DEPRECATED/NOT WORKING pelo proprio mantenedor), que precisa ser importado via abapGit "
                        + "e ativado no sistema -- nao faz parte do framework ADT padrao. Isso nao e um erro desta "
                        + "ferramenta; o sistema provavelmente nao tem esse componente instalado.");
                output.addProperty("errorBody", msg);
                return output.toString();
            }
            throw e;
        }

        JsonObject output = new JsonObject();
        output.add("repositories", AdtXmlParser.parseAbapGitRepos(response.body()));
        return output.toString();
    }
}
