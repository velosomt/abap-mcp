package com.sap.adt.mcp.tools;

import java.net.http.HttpResponse;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sap.adt.mcp.sap.AdtRestClient;
import com.sap.adt.mcp.sap.AdtXmlParser;

/**
 * Tool: sap_abapgit_repo_status -- Show the staged/unstaged/ignored object diff for one
 * abapGit repository (read-only, equivalent to "git status"). Does not stage, commit,
 * push or pull anything -- see AbapGitListReposTool's javadoc for why those were left out.
 *
 * Ported from the abap-adt-api reference client (src/api/abapgit.ts): GET the repo's
 * "stage_link", discovered from the link list returned by GET /sap/bc/adt/abapgit/repos.
 */
public class AbapGitRepoStatusTool extends AbstractMcpTool {

    public static final String NAME = "sap_abapgit_repo_status";

    public AbapGitRepoStatusTool(AdtRestClient client) {
        super(client);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return "Show the staged/unstaged/ignored object diff for one abapGit repository (read-only, "
                + "like 'git status'). Provide the repoKey from sap_abapgit_list_repos.";
    }

    @Override
    public JsonObject getInputSchema() {
        JsonObject repoKeyProp = new JsonObject();
        repoKeyProp.addProperty("type", "string");
        repoKeyProp.addProperty("description", "Repository key, as returned by sap_abapgit_list_repos.");

        JsonObject properties = new JsonObject();
        properties.add("repoKey", repoKeyProp);

        JsonArray required = new JsonArray();
        required.add("repoKey");

        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.add("properties", properties);
        schema.add("required", required);

        return schema;
    }

    @Override
    public String execute(JsonObject arguments) throws Exception {
        String repoKey = optString(arguments, "repoKey");
        if (repoKey == null || repoKey.isEmpty()) {
            throw new IllegalArgumentException("Provide repoKey.");
        }

        HttpResponse<String> reposResp;
        try {
            reposResp = client.get("/sap/bc/adt/abapgit/repos", "application/abapgit.adt.repos.v2+xml");
        } catch (Exception e) {
            String msg = e.getMessage() == null ? "" : e.getMessage();
            if (msg.contains("404") || msg.toLowerCase().contains("resourcenotfound")) {
                // Same root cause as AbapGitListReposTool: /sap/bc/adt/abapgit/repos is served
                // by the separate (deprecated upstream) abapGit ADT Backend component, which
                // isn't installed/active on this system.
                JsonObject output = new JsonObject();
                output.addProperty("status", "unavailable");
                output.addProperty("reason",
                        "O endpoint /sap/bc/adt/abapgit/repos nao existe neste backend (componente abapGit ADT "
                        + "Backend nao instalado/ativo). Veja sap_abapgit_list_repos para detalhes.");
                output.addProperty("errorBody", msg);
                return output.toString();
            }
            throw e;
        }
        JsonArray repos = AdtXmlParser.parseAbapGitRepos(reposResp.body());

        String stageLink = null;
        for (com.google.gson.JsonElement el : repos) {
            JsonObject repo = el.getAsJsonObject();
            if (!repoKey.equals(repo.get("key").getAsString())) continue;
            if (!repo.has("links")) break;
            for (com.google.gson.JsonElement linkEl : repo.getAsJsonArray("links")) {
                JsonObject link = linkEl.getAsJsonObject();
                String rel = link.has("rel") ? link.get("rel").getAsString() : "";
                String type = link.has("type") ? link.get("type").getAsString() : "";
                if (rel.toLowerCase().contains("stage") || type.toLowerCase().contains("stage")) {
                    stageLink = link.get("href").getAsString();
                    break;
                }
            }
            break;
        }

        if (stageLink == null) {
            JsonObject output = new JsonObject();
            output.addProperty("found", false);
            output.addProperty("note", "No repo with key '" + repoKey + "' (or no stage link exposed for it) "
                    + "was found via sap_abapgit_list_repos.");
            return output.toString();
        }

        HttpResponse<String> stageResp = client.get(stageLink, "application/abapgit.adt.repo.stage.v1+xml");
        JsonObject output = AdtXmlParser.parseAbapGitStage(stageResp.body());
        output.addProperty("found", true);
        return output.toString();
    }
}
