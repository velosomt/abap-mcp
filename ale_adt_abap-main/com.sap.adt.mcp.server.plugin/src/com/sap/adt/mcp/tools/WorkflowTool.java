package com.sap.adt.mcp.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sap.adt.mcp.sap.AdtRestClient;

/**
 * Tool: sap_workflow -- Orquestrador de fluxo (o "agente" dentro do MCP).
 *
 * <p>O servidor MCP é Java puro, sem LLM embutido: ele não raciocina nem
 * consegue parar no meio de uma execução para perguntar algo ao usuário. Por
 * isso este orquestrador trabalha em dois modos:</p>
 *
 * <ul>
 *   <li><b>confirmed=false (default)</b> — modo PLANEJADOR: detecta a intenção
 *       a partir de {@code goal} e devolve um plano faseado
 *       (PLAN → BUILD → TEST → REVIEW) com as ferramentas recomendadas em cada
 *       etapa e os <i>gates</i> de confirmação. Não executa nada.</li>
 *   <li><b>confirmed=true</b> — modo EXECUTOR: para o fluxo {@code create},
 *       encadeia de forma determinística as tools já existentes
 *       ({@code sap_create_and_validate} → {@code sap_run_unit_test} →
 *       {@code sap_atc_run}), reaproveitando o {@code AdtRestClient}. Nenhum
 *       endpoint ADT novo é chamado e nenhum dado é fabricado.</li>
 * </ul>
 *
 * <p><b>Regra de confirmação:</b> a tool não tem como perguntar ao usuário
 * sozinha. {@code confirmed=true} significa que o cliente (Claude) já obteve a
 * aprovação explícita do usuário para criar/editar objetos. Nunca passe
 * {@code confirmed=true} sem essa aprovação — em qualquer pacote, inclusive
 * {@code $TMP}.</p>
 */
public class WorkflowTool extends AbstractMcpTool {

    public static final String NAME = "sap_workflow";

    private static final String INTENT_CREATE = "create";
    private static final String INTENT_REFACTOR = "refactor";
    private static final String INTENT_ASSESS = "assess";
    private static final String INTENT_DOCUMENT = "document";
    private static final String INTENT_EXPLORE = "explore";

    private static final String CONFIRMATION_RULE =
            "REGRA DE CONFIRMAÇÃO: nunca crie objeto nem abra transporte (sap_create_transport) "
            + "sem o usuário aprovar explicitamente, em qualquer pacote — inclusive $TMP. "
            + "Em modo executor isso equivale a só chamar sap_workflow com confirmed=true depois do OK do usuário.";

    public WorkflowTool(AdtRestClient client) {
        super(client);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return "Orquestrador de fluxo SAP (planejar->executar->testar->revisar). Com confirmed=false (default) "
                + "retorna um PLANO faseado (PLAN/BUILD/TEST/REVIEW) com as ferramentas recomendadas e os gates "
                + "de confirmação, SEM executar nada. Com confirmed=true executa a cadeia determinística do fluxo "
                + "'create' (sap_create_and_validate -> sap_run_unit_test -> sap_atc_run). NUNCA passe confirmed=true "
                + "sem o usuário ter aprovado a criação/edição de objetos. A intenção (create/refactor/assess/document/"
                + "explore) é inferida de 'goal' e pode ser forçada via 'intent'.";
    }

    @Override
    public JsonObject getInputSchema() {
        JsonObject properties = new JsonObject();

        JsonObject goalProp = new JsonObject();
        goalProp.addProperty("type", "string");
        goalProp.addProperty("description",
                "Objetivo em texto livre (ex.: 'criar RAP BO de faturas', 'refatorar a classe ZCL_X', "
                + "'avaliar o pacote ZMM para Clean Core', 'documentar ZSD'). Usado para inferir a intenção.");
        properties.add("goal", goalProp);

        JsonObject intentProp = new JsonObject();
        intentProp.addProperty("type", "string");
        intentProp.addProperty("description",
                "Opcional. Força a intenção em vez de inferir do goal: create | refactor | assess | document | explore.");
        JsonArray intentEnum = new JsonArray();
        intentEnum.add(INTENT_CREATE);
        intentEnum.add(INTENT_REFACTOR);
        intentEnum.add(INTENT_ASSESS);
        intentEnum.add(INTENT_DOCUMENT);
        intentEnum.add(INTENT_EXPLORE);
        intentProp.add("enum", intentEnum);
        properties.add("intent", intentProp);

        JsonObject confirmedProp = new JsonObject();
        confirmedProp.addProperty("type", "boolean");
        confirmedProp.addProperty("description",
                "Default false. false = só devolve o plano (não executa). true = executa a cadeia determinística "
                + "do fluxo 'create'. Só passe true após o usuário aprovar explicitamente a criação.");
        properties.add("confirmed", confirmedProp);

        // Parâmetros de execução (modo confirmed=true, fluxo create) -- mesmos do sap_create_and_validate.
        JsonObject objtypeProp = new JsonObject();
        objtypeProp.addProperty("type", "string");
        objtypeProp.addProperty("description",
                "Execução create: type-key ADT do objeto a criar (ex.: CLAS/OC, DDLS/DF, BDEF/BO).");
        properties.add("objtype", objtypeProp);

        JsonObject nameProp = new JsonObject();
        nameProp.addProperty("type", "string");
        nameProp.addProperty("description", "Execução create: nome do objeto a criar (ex.: ZCL_INVOICE).");
        properties.add("name", nameProp);

        JsonObject parentProp = new JsonObject();
        parentProp.addProperty("type", "string");
        parentProp.addProperty("description",
                "Execução create: pacote de destino (DEVCLASS). Use $TMP só para teste descartável.");
        properties.add("parentName", parentProp);

        JsonObject transportProp = new JsonObject();
        transportProp.addProperty("type", "string");
        transportProp.addProperty("description",
                "Execução create: TRKORR a usar (opcional; se omitido e pacote != $TMP, roda sap_transport_check).");
        properties.add("transport", transportProp);

        JsonObject initialSourceProp = new JsonObject();
        initialSourceProp.addProperty("type", "string");
        initialSourceProp.addProperty("description", "Execução create: source inicial do objeto, quando o tipo suporta texto.");
        properties.add("initialSource", initialSourceProp);

        JsonObject runTestsProp = new JsonObject();
        runTestsProp.addProperty("type", "boolean");
        runTestsProp.addProperty("description",
                "Execução create: rodar a fase TEST (sap_run_unit_test) após criar uma classe. Default true.");
        properties.add("runTests", runTestsProp);

        JsonObject runReviewProp = new JsonObject();
        runReviewProp.addProperty("type", "boolean");
        runReviewProp.addProperty("description",
                "Execução create: rodar a fase REVIEW (sap_atc_run) após criar. Default true.");
        properties.add("runReview", runReviewProp);

        JsonObject atcVariantProp = new JsonObject();
        atcVariantProp.addProperty("type", "string");
        atcVariantProp.addProperty("description", "Execução create: variante do sap_atc_run na fase REVIEW. Default DEFAULT.");
        properties.add("atcVariant", atcVariantProp);

        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.add("properties", properties);

        return schema;
    }

    @Override
    public String execute(JsonObject arguments) throws Exception {
        String goal = optString(arguments, "goal");
        String intent = optString(arguments, "intent");
        if (intent == null || intent.isEmpty()) {
            if (goal == null || goal.isEmpty()) {
                throw new IllegalArgumentException("Provide 'goal' (or 'intent').");
            }
            intent = detectIntent(goal);
        }
        intent = intent.toLowerCase();

        boolean confirmed = optBool(arguments, "confirmed", false);

        JsonObject plan = buildPlan(intent);

        if (!confirmed) {
            plan.addProperty("mode", "plan");
            plan.addProperty("nextStep",
                    "Revise o plano com o usuário. Após aprovação explícita, para o fluxo 'create' chame sap_workflow "
                    + "de novo com confirmed=true + objtype/name/parentName (e initialSource/transport se aplicável). "
                    + "Para refactor/assess/document/explore, conduza os passos do plano individualmente.");
            return plan.toString();
        }

        // --- Modo executor (confirmed=true) ---
        JsonObject out = new JsonObject();
        out.addProperty("intent", intent);
        out.addProperty("mode", "execute");
        out.add("plannedPhases", plan.getAsJsonArray("phases"));

        if (!INTENT_CREATE.equals(intent)) {
            out.addProperty("note",
                    "Execução automática faseada está implementada apenas para o fluxo 'create'. Para '" + intent
                    + "', siga os passos do plano acima individualmente (ou use o agente especializado correspondente).");
            return out.toString();
        }

        String objtype = optString(arguments, "objtype");
        String name = optString(arguments, "name");
        String parentName = optString(arguments, "parentName");
        if (objtype == null || name == null || parentName == null) {
            throw new IllegalArgumentException(
                    "confirmed=true no fluxo 'create' exige objtype, name e parentName.");
        }

        // FASE BUILD -- reutiliza o orquestrador de criação (transport_check -> create -> syntax_check).
        String buildRes = new CreateAndValidateTool(client).execute(arguments);
        JsonObject buildJson = JsonParser.parseString(buildRes).getAsJsonObject();
        out.add("build", buildJson);

        boolean created = buildJson.has("creation")
                && buildJson.getAsJsonObject("creation").has("status")
                && "created".equals(buildJson.getAsJsonObject("creation").get("status").getAsString());
        if (!created) {
            out.addProperty("note",
                    "FASE BUILD não confirmou status=created; TEST e REVIEW não foram executados. "
                    + "Leia o erro em 'build' (anotação obrigatória faltando, dependência inativa, transporte, etc.).");
            return out.toString();
        }

        // FASE TEST -- só faz sentido para classes (ABAP Unit).
        boolean runTests = optBool(arguments, "runTests", true);
        if (runTests && objtype.toUpperCase().startsWith("CLAS")) {
            JsonObject testArgs = new JsonObject();
            testArgs.addProperty("objectType", objtype);
            testArgs.addProperty("objectName", name);
            try {
                out.add("test", JsonParser.parseString(new RunUnitTestTool(client).execute(testArgs)));
            } catch (Exception e) {
                out.addProperty("testError", e.getMessage());
            }
        } else {
            out.addProperty("testNote",
                    "FASE TEST pulada (objeto não é classe ou runTests=false). Crie testes com sap_create_or_update_test_class se aplicável.");
        }

        // FASE REVIEW -- ATC (Clean Core).
        boolean runReview = optBool(arguments, "runReview", true);
        if (runReview) {
            JsonObject reviewArgs = new JsonObject();
            reviewArgs.addProperty("objectType", objtype);
            reviewArgs.addProperty("objectName", name);
            String atcVariant = optString(arguments, "atcVariant");
            if (atcVariant != null && !atcVariant.isEmpty()) {
                reviewArgs.addProperty("variant", atcVariant);
            }
            try {
                out.add("review", JsonParser.parseString(new AtcRunTool(client).execute(reviewArgs)));
            } catch (Exception e) {
                out.addProperty("reviewError", e.getMessage());
            }
        }

        return out.toString();
    }

    // ---------------------------------------------------------------- helpers

    private boolean optBool(JsonObject obj, String key, boolean defaultValue) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) {
            return defaultValue;
        }
        try {
            return obj.get(key).getAsBoolean();
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private String detectIntent(String goal) {
        String g = goal.toLowerCase();
        if (containsAny(g, "avali", "assess", "clean core", "compliance", "atc", "migra", "prontid", "nível", "nivel", "qualidade")) {
            return INTENT_ASSESS;
        }
        if (containsAny(g, "document", "doc ", "documenta", "descrever", "onboarding")) {
            return INTENT_DOCUMENT;
        }
        if (containsAny(g, "refator", "refactor", "renome", "rename", "extrair", "extract", "limpar", "otimiz", "mover", "melhorar")) {
            return INTENT_REFACTOR;
        }
        if (containsAny(g, "cri", "create", "scaffold", "nov", "gerar", "gere", "montar", "implementar", "build")) {
            return INTENT_CREATE;
        }
        return INTENT_EXPLORE;
    }

    private boolean containsAny(String haystack, String... needles) {
        for (String n : needles) {
            if (haystack.contains(n)) {
                return true;
            }
        }
        return false;
    }

    private JsonObject step(String tool, String why, boolean requiresConfirmation) {
        JsonObject s = new JsonObject();
        s.addProperty("tool", tool);
        s.addProperty("why", why);
        if (requiresConfirmation) {
            s.addProperty("requiresConfirmation", true);
        }
        return s;
    }

    private JsonObject phase(String name, String description, JsonObject... steps) {
        JsonObject p = new JsonObject();
        p.addProperty("phase", name);
        p.addProperty("description", description);
        JsonArray arr = new JsonArray();
        for (JsonObject s : steps) {
            arr.add(s);
        }
        p.add("steps", arr);
        return p;
    }

    private JsonObject planEnvelope(String intent, boolean requiresConfirmation, JsonArray phases, String... notes) {
        JsonObject plan = new JsonObject();
        plan.addProperty("intent", intent);
        plan.addProperty("requiresConfirmation", requiresConfirmation);
        plan.add("phases", phases);
        JsonArray notesArr = new JsonArray();
        for (String n : notes) {
            notesArr.add(n);
        }
        plan.add("notes", notesArr);
        return plan;
    }

    private JsonObject buildPlan(String intent) {
        JsonArray phases = new JsonArray();

        switch (intent) {
            case INTENT_CREATE:
                phases.add(phase("PLAN", "Confirmar capacidade do backend e modelar o objeto antes de criar.",
                        step("sap_discovery", "Confirmar que a capacidade/endpoint (RAP, business services, etc.) existe NESTE backend — evita descobrir por erro 404/415.", false),
                        step("sap_object_types", "Validar o type-key ADT correto do objeto a criar.", false),
                        step("sap_search_object", "Checar se o nome Z* já existe e aplicar a convenção Clean Core (ZI_ interface, ZC_ projection, ZBP_ behavior pool).", false),
                        step("sap_get_table_schema", "Se for criar sobre tabela existente, entender campos e chaves.", false)));
                phases.add(phase("BUILD", "Criar o objeto — só após confirmação explícita do usuário.",
                        step("sap_pretty_print", "Formatar o source ABAP/BDL antes de gravar.", false),
                        step("sap_create_and_validate", "Cria o objeto e já roda transport_check (pacote != $TMP) + syntax_check. GATE: só após o usuário confirmar pacote e criação.", true)));
                phases.add(phase("TEST", "Validar comportamento.",
                        step("sap_create_or_update_test_class", "Criar/atualizar classe de teste ABAP Unit, se aplicável.", false),
                        step("sap_run_unit_test", "Rodar ABAP Unit no objeto criado.", false)));
                phases.add(phase("REVIEW", "Garantir qualidade e Clean Core.",
                        step("sap_atc_run", "Rodar ATC no objeto criado e classificar findings.", false),
                        step("sap_usage_references", "Mapear dependências e onde o novo objeto será usado.", false)));
                return planEnvelope(intent, true, phases,
                        "Clean Core: CDS view = só modelo de dados; lógica vai para behavior. Anotações de UI sempre via Metadata Extension (DDLX), nunca na própria view. Separe camada interface (ZI_) da projection (ZC_).",
                        "Tipos form-only (DOMA, DTEL, MSAG, SRVB, FUGR/F) ignoram initialSource — avise o usuário do que não pôde ser configurado.",
                        CONFIRMATION_RULE);

            case INTENT_REFACTOR:
                phases.add(phase("PLAN", "Entender o objeto e o raio de impacto antes de mexer.",
                        step("sap_explain_object", "Estrutura + source + where-used numa só chamada.", false),
                        step("sap_usage_references", "Raio de impacto: quem usa o objeto.", false),
                        step("sap_type_hierarchy", "Sub/super-tipos afetados (polimorfismo) antes de alterar assinatura ou renomear.", false)));
                phases.add(phase("BUILD", "Aplicar a mudança — só após confirmação.",
                        step("sap_evaluate_rename", "Pré-visualizar o impacto de um rename antes de aplicar.", false),
                        step("sap_evaluate_extract_method", "Pré-visualizar extração de método.", false),
                        step("sap_pretty_print", "Formatar o trecho antes de gravar.", false),
                        step("sap_replace_source_content", "Aplicar a mudança pontual (preferível a sap_set_source). GATE: confirmar com o usuário.", true)));
                phases.add(phase("TEST", "Garantir que nada quebrou.",
                        step("sap_syntax_check", "Checar sintaxe após editar.", false),
                        step("sap_run_unit_test", "Rodar os testes existentes.", false)));
                phases.add(phase("REVIEW", "Confirmar que não introduziu findings.",
                        step("sap_atc_run", "Rodar ATC após a refatoração.", false)));
                return planEnvelope(intent, true, phases,
                        "Prefira sap_replace_source_content (troca um trecho) a sap_set_source (reescreve o arquivo inteiro) para mudanças pontuais.",
                        CONFIRMATION_RULE);

            case INTENT_ASSESS:
                phases.add(phase("PLAN", "Descobrir o escopo (read-only).",
                        step("sap_search_object", "Descobrir objetos Z/Y do pacote. Atenção: packageName vem vazio para DDLS/DF — confirme a lista com o usuário.", false),
                        step("sap_object_types", "Confirmar os type-keys dos objetos encontrados.", false)));
                phases.add(phase("ANALYZE", "Avaliar compliance Clean Core (read-only).",
                        step("sap_atc_run", "Rodar ATC por objeto e classificar A-D (pior finding define o nível).", false),
                        step("sap_usage_references", "Raio de explosão de cada objeto para priorizar.", false),
                        step("sap_get_migration_analysis", "Análise de migração. ATENÇÃO: pode vir mocked=true (achados fabricados) — repasse o warning literal e não trate como real.", false)));
                phases.add(phase("REPORT", "Consolidar.",
                        step("(sem tool)", "Gerar resumo separando A (verificado) de A* (não verificado). Considere o agente sap-clean-core-checker para o fluxo em lote com checkpoint.", false)));
                return planEnvelope(intent, false, phases,
                        "assess é read-only — não cria nem altera objetos.",
                        "Nunca afirme 'Level A confirmado' sem verificar que o sap_atc_run realmente populou findings nesta sessão.");

            case INTENT_DOCUMENT:
                phases.add(phase("PLAN", "Descobrir o escopo (read-only).",
                        step("sap_search_object", "Descobrir objetos do pacote.", false),
                        step("sap_get_package_tree", "Ver a árvore do pacote para contexto.", false)));
                phases.add(phase("COLLECT", "Coletar evidência real do código.",
                        step("sap_get_source", "Ler o source (use o type-key composto exato, ex.: CLAS/OC).", false),
                        step("sap_object_structure", "Estrutura/includes/métodos.", false),
                        step("sap_element_info", "Assinaturas e declarações exatas de elementos no cursor.", false),
                        step("sap_type_hierarchy", "Heranças/implementações para documentar relações.", false),
                        step("sap_usage_references", "Dependências e consumidores.", false)));
                phases.add(phase("WRITE", "Gerar a documentação.",
                        step("(sem tool)", "Gerar doc dual-audiência (dev + negócio) a partir da evidência real, nunca texto genérico. Considere o agente sap-custom-code-documenter.", false)));
                return planEnvelope(intent, false, phases,
                        "document é read-only. Só documente código customizado (Z/Y), nunca objetos SAP padrão.");

            case INTENT_EXPLORE:
            default:
                phases.add(phase("EXPLORE", "Entender uma base ABAP sem alterar nada (read-only).",
                        step("sap_search_object", "Localizar objetos por nome/tipo.", false),
                        step("sap_explain_object", "Estrutura + source + where-used (+ enhancements) numa chamada.", false),
                        step("sap_find_definition", "Go-to-definition de símbolos no código.", false),
                        step("sap_type_hierarchy", "Sub/super-tipos de classes e interfaces.", false),
                        step("sap_element_info", "Metadados (tipo, assinatura, doc) de um elemento no cursor.", false),
                        step("sap_code_completion", "Descobrir nomes válidos (campos/métodos/keywords) em vez de adivinhar sintaxe.", false)));
                return planEnvelope(INTENT_EXPLORE, false, phases,
                        "explore é 100% read-only — ideal para entender o cenário antes de planejar uma criação/refatoração.");
        }
    }
}
