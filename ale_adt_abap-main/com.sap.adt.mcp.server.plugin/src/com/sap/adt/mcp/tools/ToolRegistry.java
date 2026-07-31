package com.sap.adt.mcp.tools;

import java.util.ArrayList;
import java.util.List;

import com.sap.adt.mcp.sap.AdtRestClient;

/**
 * Fonte única da lista de tools MCP. Usada tanto pelo plugin Eclipse
 * (McpServerView) quanto pelo entrypoint standalone (StandaloneMain), para que
 * as duas formas de rodar o servidor exponham exatamente o mesmo conjunto de
 * ferramentas. Java puro — sem dependência de Eclipse/OSGi.
 */
public final class ToolRegistry {

    private ToolRegistry() {
    }

    public static List<McpTool> createAll(AdtRestClient adtClient) {
        List<McpTool> tools = new ArrayList<>();

        // Core object operations
        tools.add(new SearchObjectTool(adtClient));
        tools.add(new GetSourceTool(adtClient));
        tools.add(new SetSourceTool(adtClient));
        tools.add(new ReplaceSourceContentTool(adtClient));
        tools.add(new ObjectStructureTool(adtClient));

        // Lock management
        tools.add(new LockTool(adtClient));
        tools.add(new UnlockTool(adtClient));

        // Syntax and activation
        tools.add(new SyntaxCheckTool(adtClient));
        tools.add(new SyntaxCheckSourceTool(adtClient));
        tools.add(new ActivateTool(adtClient));
        tools.add(new ActivateBatchTool(adtClient));
        tools.add(new InactiveObjectsTool(adtClient));

        // Object creation & execution & deletion
        tools.add(new CreateObjectTool(adtClient));
        tools.add(new AbapCreationCreateObjectTool(adtClient));
        tools.add(new ExecuteConsoleTool(adtClient));
        tools.add(new RunProgramTool(adtClient));
        tools.add(new DeleteObjectTool(adtClient));

        // Transaction codes & user parameters (sem endpoint REST direto -- via helper class classrun)
        tools.add(new CreateTransactionTool(adtClient));
        tools.add(new DeleteTransactionTool(adtClient));
        tools.add(new GetUserParametersTool(adtClient));
        tools.add(new SetUserParametersTool(adtClient));

        // ADT Generators (Official SAP Fallbacks)
        tools.add(new AbapGeneratorsListTool(adtClient));
        tools.add(new AbapGeneratorsGetSchemaTool(adtClient));
        tools.add(new AbapGeneratorsGenerateObjectsTool(adtClient));
        tools.add(new AbapActivateObjectsTool(adtClient));

        // Testing and quality
        tools.add(new RunUnitTestTool(adtClient));
        tools.add(new CreateOrUpdateTestClassTool(adtClient));
        tools.add(new GetTestClassesTool(adtClient));
        tools.add(new AtcRunTool(adtClient));
        tools.add(new AtcQuickfixEvaluateTool(adtClient));
        tools.add(new AtcQuickfixApplyTool(adtClient));

        // Transport
        tools.add(new TransportCheckTool(adtClient));
        tools.add(new GetTransportRequestsTool(adtClient));
        tools.add(new TransportDetailsTool(adtClient));
        tools.add(new CreateTransportTool(adtClient));

        // Analysis
        tools.add(new UsageReferencesTool(adtClient));
        tools.add(new SqlQueryTool(adtClient));
        tools.add(new SearchRepositoryTool(adtClient));
        tools.add(new SearchTransportsTool(adtClient));
        tools.add(new GetTableSchemaTool(adtClient));
        tools.add(new GetMigrationAnalysisTool(adtClient));
        tools.add(new GetRevisionsTool(adtClient));
        tools.add(new GetEnhancementsTool(adtClient));
        tools.add(new GetPackageTreeTool(adtClient));
        tools.add(new GetShortDumpsTool(adtClient));
        tools.add(new EvaluateRenameTool(adtClient));
        tools.add(new EvaluateExtractMethodTool(adtClient));

        // Text elements
        tools.add(new GetTextElementsTool(adtClient));
        tools.add(new SetTextElementsTool(adtClient));

        // abapGit (read-only)
        tools.add(new AbapGitListReposTool(adtClient));
        tools.add(new AbapGitRepoStatusTool(adtClient));

        // Documentation
        tools.add(new AbapDocuTool(adtClient));

        // Code productivity & navigation (read-only / non-destructive)
        tools.add(new PrettyPrintTool(adtClient));
        tools.add(new CodeCompletionTool(adtClient));
        tools.add(new ElementInfoTool(adtClient));
        tools.add(new FindDefinitionTool(adtClient));
        tools.add(new TypeHierarchyTool(adtClient));

        // Backend discovery (read-only)
        tools.add(new DiscoveryTool(adtClient));
        tools.add(new ObjectTypesTool(adtClient));

        // Orquestradoras (combinam tools já existentes, sem endpoint novo)
        tools.add(new ExplainObjectTool(adtClient));
        tools.add(new CreateAndValidateTool(adtClient));
        tools.add(new AtcAutofixTool(adtClient));
        tools.add(new WorkflowTool(adtClient));

        // Search & surgical edit (ported from vsp)
        tools.add(new GrepObjectTool(adtClient));
        tools.add(new GrepPackageTool(adtClient));
        tools.add(new EditSourceTool(adtClient));
        tools.add(new CompareSourceTool(adtClient));
        tools.add(new ExecuteAbapTool(adtClient));

        // Debugger interativo (/sap/bc/adt/debugger) -- sessao stateful + listener em background
        tools.add(new AleDebugMasterTool(adtClient));

        return tools;
    }
}
