# 🚀 ALÊ ADT ABAP 2.0 💎

[![SAP](https://img.shields.io/badge/SAP-008FD3?style=for-the-badge&logo=sap&logoColor=white)](https://www.sap.com/)
[![Protocol](https://img.shields.io/badge/MCP-Protocol-orange?style=for-the-badge)](https://modelcontextprotocol.io/)
[![ABAP](https://img.shields.io/badge/ABAP-RAP-red?style=for-the-badge)](https://help.sap.com/viewer/922d967aed9a4317a762a4c712c53107/7.5.0/en-US/6n6b6b6b6b6b6b6b6b6b6b6b6b6b6b6b.html)

Uma ponte inteligente e personalizada entre o **Eclipse ADT** e o **Claude Code**. Este plugin transforma seu ambiente Eclipse em um servidor MCP (Model Context Protocol), permitindo que agentes de IA interajam diretamente com seu sistema SAP para desenvolver, testar e analisar código ABAP.

> **Nota de Crédito:** Este projeto é uma evolução personalizada e ampliada do projeto original [sap-adt-mcp-server](https://github.com/YahorNovik/sap-adt-mcp-server) de Yahor Novik. Veja [Créditos e Fontes Técnicas](#-créditos-e-fontes-técnicas) para a lista completa de referências usadas para validar e corrigir cada funcionalidade.

---

## 📑 Índice

1. [O que há de novo](#-o-que-há-de-novo-no-alê-adt-abap)
2. [Funcionalidades Principais](#️-funcionalidades-principais-arsenal-do-agente)
3. [Como funciona por dentro](#️-como-funciona-por-dentro-arquitetura)
4. [Compatibilidade RAP130](#-compatibilidade-total-sap-rap130--agente-customizado-agentmd)
5. [Lista completa de ferramentas MCP](#-lista-completa-de-ferramentas-mcp-69)
6. [Referência Técnica de Objetos](#-referência-técnica-de-objetos)
7. [Status Conhecido (limitações)](#-status-conhecido-limitações)
8. [Agentes Claude Code prontos](#-agentes-claude-code-prontos-agents)
9. [Roadmap](#️-roadmap-e-funcionalidades-futuras-to-do)
10. [Como Começar (Eclipse)](#-como-começar)
11. [Rodar Localmente sem Eclipse (Standalone)](#-rodar-localmente-sem-eclipse-standalone)
12. [Para Desenvolvedores](#️-para-desenvolvedores-git-clone--build)
13. [Créditos e Fontes Técnicas](#-créditos-e-fontes-técnicas)
14. [Licença](#-licença)

---

## ✨ O que há de novo no ALÊ ADT ABAP?

Diferente da versão padrão, esta edição foi estendida para suportar o desenvolvimento moderno em SAP:

- **📦 Criação de 19 tipos de objeto ABAP:** clássicos (Program, Include, Class, Interface, Function Group/Module/Include, Table, Structure, Data Element, Domain, Message Class) e RAP (CDS View, Access Control/DCL, Metadata Extension, Annotation Definition, Service Definition, Service Binding, Behavior Definition) — sem nenhuma dependência de classe Z auxiliar/classrun.
- **🏗️ Arquitetura de Registry:** Sistema genérico (`AdtObjectRegistry`) que centraliza URL de criação, namespace XML e metadados de cada tipo.
- **🔤 Aliases de tipo:** códigos curtos e amigáveis (`CDS`, `TABLE`, `DOMAIN`, `FUNC`...) normalizados automaticamente para a chave técnica TADIR via `AdtTypeAlias`.
- **📦 Transporte + 🔧 ATC quickfixes:** `sap_transport_check`, `sap_get_transport_requests`, `sap_atc_quickfix_evaluate` e `sap_atc_quickfix_apply`.
- **🔄 Ativação em lote e migration analysis:** `sap_activate_batch` (dependência circular entre objetos) e `sap_get_migration_analysis` (sinaliza `mocked: true` quando o endpoint real falha).
- **🧪 Test classes (ABAP Unit) dedicadas:** `sap_get_test_classes` e `sap_create_or_update_test_class`.
- **🌐 Auditoria, refatoração e abapGit:** `sap_get_revisions`, `sap_get_enhancements`, `sap_get_text_elements`/`sap_set_text_elements`, `sap_get_package_tree`, `sap_get_short_dumps`, `sap_evaluate_rename`/`sap_evaluate_extract_method`, `sap_abapgit_list_repos`/`sap_abapgit_repo_status` — ver detalhes na lista de ferramentas abaixo.
- **🧩 Orquestradoras (combinam tools existentes, sem endpoint novo):** `sap_explain_object`, `sap_create_and_validate`, `sap_atc_autofix` — reduzem fluxos de 3-4 chamadas manuais a uma só.
- **🎨 Personalização:** Identidade visual e interface adaptadas para o terminal **ALÊ ADT ABAP**.

---

## 🛠️ Funcionalidades Principais (Arsenal do Agente)

Esta versão eleva o agente de IA a um verdadeiro **Desenvolvedor Sênior ABAP**, concedendo acesso a quase todas as funcionalidades do Eclipse ADT através das ferramentas abaixo.

### 🔍 Exploração, Busca e Análise de Estruturas
Estas ferramentas permitem que a IA compreenda o ecossistema e analise o código antes de modificar qualquer coisa.
- **`sap_search_object`**: Faz uma varredura completa no repositório SAP (ex: `ZRPSD*`). Aceita curingas e tipos específicos, essencial para encontrar programas e classes perdidas.
- **`sap_get_source`**: Extrai o código-fonte ABAP bruto de Classes, Programas, Interfaces, FMs, CDS Views e muito mais.
- **`sap_object_structure`**: Retorna a árvore de metadados do objeto (includes, atributos, métodos, componentes).
- **`sap_get_table_schema`**: Vai fundo no Dicionário de Dados (DDIC) e retorna as colunas, tipos e descrições de tabelas de banco de dados (`DD03L`), permitindo à IA entender modelos de dados sem rodar SQL.
- **`sap_usage_references`**: O clássico "Onde Usado". Descobre todos os locais onde um objeto ou método é referenciado, crucial para análise de impacto antes de refatorações.
- **`sap_abap_docu`**: Acesso direto à documentação da linguagem ABAP (F1 do Eclipse). A IA pode tirar dúvidas de sintaxe e comandos na própria fonte oficial da SAP.

### ✍️ Desenvolvimento Cirúrgico e Modificações
O núcleo de codificação. As ferramentas de modificação possuem travas de segurança rigorosas.
- **`sap_create_object`** (alias `abap_creation-create_object`): Cria objetos do zero usando os templates nativos do ADT — programas, classes, interfaces, function groups/modules, CDS views, DCL, metadata/annotation extensions, tabelas, estruturas, elementos de dados, domínios, classes de mensagem, service definitions/bindings e behavior definitions. Aceita o parâmetro opcional `initialSource` para gravar a fonte inicial (lock→write→activate→unlock) na mesma chamada, quando o tipo suporta fonte textual. Veja a [Referência Técnica de Objetos](#-referência-técnica-de-objetos). Nenhum tipo depende de classe Z auxiliar.
- **`sap_replace_source_content`**: A ferramenta **mais segura e recomendada** para edições. A IA passa o trecho antigo (`targetContent`) e o novo (`replacementContent`). O MCP vai ao SAP, faz o patch exato apenas daquele bloco, sem risco de apagar o restante do arquivo por limite de tokens ou falhas de contexto.
- **`sap_set_source`**: Substitui o arquivo inteiro. Possui um aviso **CRÍTICO** embutido: a IA é rigidamente instruída a fornecer 100% do código fonte sem abreviações, ou sofrerá punição, garantindo que objetos não sejam truncados.
- **`sap_delete_object`**: Exclui definitivamente um objeto do repositório SAP. Essencial para limpezas e reversões de erros.
- **`sap_lock` e `sap_unlock`**: Travamento manual e liberação de objetos em edições exclusivas.
- **`sap_inactive_objects`**: Lista todos os objetos do seu usuário que ainda precisam ser ativados.
- **`sap_activate`**: Faz a ativação em massa (ou unitária) dos objetos para validar alterações de código no ambiente de compilação do SAP.
- **`sap_activate_batch`**: Ativa múltiplos objetos em uma única chamada via `/sap/bc/adt/activation/runs`. Diferente de `sap_activate` (um objeto por chamada), isso resolve dependência circular — ex. uma CDS interface view e sua projection view que se referenciam mutuamente, onde ativar qualquer uma isolada falha. Faz polling do status até `finished` (timeout configurável via `maxWaitTime`/`pollInterval`) e devolve as mensagens por objeto.
- **`sap_get_test_classes`**: Lê o include ABAP Unit (`/includes/testclasses`) de uma classe, com fallback automático ativo→inativo.
- **`sap_create_or_update_test_class`**: Cria/sobrescreve o include de teste a partir de uma lista de métodos, gerando o skeleton `LTC DEFINITION/IMPLEMENTATION` automaticamente.
- **`sap_get_text_elements`** / **`sap_set_text_elements`**: Lê/grava os text elements (textos de tela, símbolos, cabeçalhos) de PROG/CLAS/FUGR — invisíveis para `sap_get_source`.

### 📦 Transporte (CTS)
- **`sap_transport_check`**: Antes de criar/alterar um objeto fora do pacote `$TMP`, consulta `/sap/bc/adt/cts/transportchecks` para descobrir qual(is) transporte(s) (TRKORR) estão disponíveis/recomendados para aquele objeto + pacote — nunca inventa um número de transporte.
- **`sap_get_transport_requests`**: Lista os transportes (TRKORR) que o usuário já possui em aberto, via `/sap/bc/adt/cts/transportrequests`, opcionalmente filtrado por `target`. Complementa `sap_transport_check` — este lista o que já existe para reaproveitar, aquele descobre o que uma mudança *nova* precisa.

### 🕵️ Auditoria, Navegação e Diagnóstico
- **`sap_get_revisions`**: Histórico de versões/revisões de um objeto (quem mudou e quando).
- **`sap_get_enhancements`**: Lista enhancements (BAdI/spot/ponto explícito) ativos no código de um objeto.
- **`sap_get_package_tree`**: Lista sub-pacotes e objetos dentro de um pacote ABAP — a árvore do Project Explorer.
- **`sap_get_short_dumps`**: Lista short dumps recentes (ST22), com filtro `query` opcional.

### 🔬 Refatoração — Análise de Impacto
- **`sap_evaluate_rename`** / **`sap_evaluate_extract_method`**: Mostram o que o SAP propõe afetar/extrair antes de aplicar manualmente (via `sap_set_source`/`sap_replace_source_content`); só avaliam, não aplicam a mudança.

### 🌿 abapGit (somente leitura)
- **`sap_abapgit_list_repos`**: Lista os repositórios abapGit vinculados a pacotes (chave, pacote, URL remota, branch, status).
- **`sap_abapgit_repo_status`**: Diff staged/unstaged/ignored de um `repoKey` — equivalente a `git status`.

### 🧪 Qualidade, Testes e Automações Executáveis
O verdadeiro superpoder de automação! A IA pode rodar código no servidor e atestar qualidade.
- **`sap_execute_console`**: Permite rodar classes ABAP Console (`IF_OO_ADT_CLASSRUN`) e capturar o output em tempo real! A IA pode criar pequenos scripts temporários, rodar e ver os logs (ótimo para automação, carga de dados ou testes rápidos).
- **`sap_sql_query`**: Uma interface de Data Preview direto no banco de dados. A IA pode escrever queries Open SQL (`SELECT * FROM mara UP TO 10 ROWS`) e ver os dados reais tabulados no prompt.
- **`sap_syntax_check`**: Varre o código fonte em busca de erros de sintaxe (como ponto final esquecido) sem precisar ativar o objeto.
- **`sap_run_unit_test`**: Executa as classes de teste ABAP Unit de um programa e traz o relatório de asserts e falhas.
- **`sap_atc_run`**: Roda o temido **ABAP Test Cockpit (ATC)** para apontar problemas de performance, segurança e clean code. O run no SAP é assíncrono — esta ferramenta inicia o run, faz *polling* do status até ele terminar e só então busca o resultado, em vez de devolver um worklist vazio.
- **`sap_atc_quickfix_evaluate`** / **`sap_atc_quickfix_apply`**: a partir de um finding do `sap_atc_run` (campo `quickfixInfo`), consulta quais correções automáticas o SAP pode oferecer e qual seria o código corrigido. `apply` só **retorna a proposta** — nunca grava no SAP por conta própria; aplicar de fato é uma chamada explícita de `sap_set_source` feita por você.
- **`sap_get_migration_analysis`** ⚠️: análise de migração; quando o endpoint real falha, devolve achados de fallback sinalizados via `"mocked": true` — nunca trate como achado real sem checar esse campo.

---

## 🏗️ Como funciona por dentro (Arquitetura)

Todo o motor de criação/edição de objetos gira em torno de três peças que conversam entre si — entender isso ajuda a saber exatamente o que esperar de cada chamada e por que nenhuma delas depende de uma classe Z:

1. **`AdtObjectRegistry`** (fonte única da verdade): um mapa estático que, para cada tipo TADIR (`PROG/P`, `DDLS/DF`, `FUGR/FF`...), guarda a URL de criação REST, o `Content-Type`, o template XML (com placeholders `${name}`, `${description}`, `${packageName}`, `${parentUrl}`) e dois metadados:
   - `ParentKind` — `PACKAGE` (a maioria: `parentName` é um pacote ABAP) ou `FUNCTION_GROUP` (módulos de função e includes de FUGR, cujo "pai" é o grupo de funções que os contém — a URL de criação usa um `%s` substituído pelo nome do grupo).
   - `supportsSource` — se o objeto tem um endpoint `/source/main` editável como texto puro (a maioria) ou se é um objeto só-formulário (Domínio, Elemento de Dados, Classe de Mensagem, Service Binding, Function Group container), cujo conteúdo detalhado só pode ser completado no Eclipse.
2. **`AdtTypeAlias`**: normaliza qualquer entrada (`CDS`, `cds`, `DDLS/DF`, `table`...) para a chave canônica antes de consultar o registry — assim toda ferramenta (criação, leitura, lock, delete) aceita os mesmos sinônimos.
3. **`AdtSourceWriter`**: implementa o fluxo **lock → write → activate → unlock** uma única vez, com retry automático em caso de `HTTP 423` (objeto temporariamente travado por outra sessão) e *unlock* garantido mesmo se a ativação falhar. `sap_set_source`, `sap_replace_source_content` e o parâmetro `initialSource` de `sap_create_object` todos chamam esse mesmo helper — zero duplicação de lógica de bloqueio.

**Fluxo de criação de um objeto com fonte (ex: uma CDS View):**
```
sap_create_object(objtype="DDLS/DF", name="ZI_MEUVIEW", parentName="$TMP",
                   description="...", initialSource="@AbapCatalog...")
   │
   ├─ 1. AdtTypeAlias.normalize("DDLS/DF") -> "DDLS/DF"
   ├─ 2. AdtObjectRegistry.getDefinition("DDLS/DF") -> template + URL + supportsSource=true
   ├─ 3. POST /sap/bc/adt/ddic/ddl/sources  (cria o "shell" do objeto a partir do template XML)
   └─ 4. AdtSourceWriter.lockWriteUnlock(...) sobre .../zi_meuview/source/main
        ├─ LOCK (accessMode=MODIFY) -> lockHandle
        ├─ PUT do código-fonte completo
        ├─ POST /sap/bc/adt/activation?method=activate
        └─ UNLOCK (sempre executado, mesmo se a ativação falhar)
```

Para tipos **só-formulário** (`supportsSource=false`), o passo 3 é o único que acontece — `sap_create_object` cria o shell e devolve uma nota avisando que o conteúdo precisa ser completado manualmente no Eclipse (não há tentativa silenciosa de gravar fonte onde não existe `/source/main`).

---

## 🤝 Compatibilidade Total: SAP RAP130 & Agente Customizado (`agent.md`)

A Versão 2.0.0 foi projetada para ser **100% compatível** com o workflow ensinado no treinamento oficial [SAP RAP130](https://github.com/SAP-samples/abap-platform-rap130).

Se você quer que o seu Claude Code aja exatamente como o gerador de "OData UI Service from Scratch" da SAP:
1. Copie o arquivo **`agent.md`** disponível na raiz deste repositório para o seu diretório de trabalho.
2. O arquivo contém as regras exatas recomendadas pela SAP (forçando o uso das ferramentas `abap_generators-*` e `abap_creation-*`).
3. O **ALÊ ADT ABAP** possui um mecanismo de *Smart Proxy* interno que vai interceptar os pedidos do gerador em massa e orquestrar a sua IA para criar toda a arquitetura RAP (Tabela, CDS Base, CDS Projection, Metadata, Behavior, Services) sequencialmente e de forma 100% segura para o seu ambiente Eclipse!

---

## 📡 Lista Completa de Ferramentas MCP (69)

Toda ferramenta abaixo é exposta via MCP (Streamable HTTP) e pode ser chamada diretamente pelo Claude Code. Aliases (`abap_*`) existem para compatibilidade com o `agent.md`/RAP130 e apontam para a mesma implementação da ferramenta `sap_*` correspondente.

| Categoria | Ferramenta MCP | O que faz |
|---|---|---|
| Busca/Leitura | `sap_search_object` | Varredura no repositório SAP por nome/tipo, com curingas (`ZRPSD*`) |
| Busca/Leitura | `sap_get_source` | Lê o código-fonte bruto de um objeto |
| Busca/Leitura | `sap_object_structure` | Árvore de metadados (includes, atributos, métodos) |
| Busca/Leitura | `sap_get_table_schema` | Colunas/tipos/descrições de uma tabela (DD03L) |
| Busca/Leitura | `sap_usage_references` | "Onde usado" — todos os lugares que referenciam um objeto |
| Busca/Leitura | `sap_abap_docu` | Documentação oficial da linguagem ABAP (F1) |
| Criação | `sap_create_object` / `abap_creation-create_object` | Cria qualquer um dos 19 tipos suportados a partir do `AdtObjectRegistry`; aceita `initialSource`. Para `SRVB/SRV`, aceita também `serviceDefinition` (obrigatório), `bindingType`, `bindingCategory` e `bindingVersion` |
| Edição | `sap_set_source` | Sobrescreve o arquivo inteiro (exige fonte 100% completa) |
| Edição | `sap_replace_source_content` | Troca um bloco exato de texto — edição cirúrgica, sem risco de truncar o resto |
| Edição | `sap_delete_object` | Exclui um objeto definitivamente |
| Edição | `sap_lock` | Trava um objeto manualmente, retorna o `lockHandle` |
| Edição | `sap_unlock` | Libera a trava de um objeto |
| Edição | `sap_activate` / `abap_activate-objects` | Ativa um ou mais objetos inativos |
| Edição | `sap_activate_batch` | Ativa múltiplos objetos juntos numa única run — resolve dependência circular entre objetos que se referenciam mutuamente |
| Edição | `sap_inactive_objects` | Lista objetos do usuário pendentes de ativação |
| Edição | `sap_get_test_classes` | Lê o include ABAP Unit de uma classe (ativo, com fallback para inativo) |
| Edição | `sap_create_or_update_test_class` | Cria/sobrescreve o include de teste, gerando o skeleton `LTC` a partir de uma lista de métodos |
| Edição | `sap_get_text_elements` | Lê text elements (textos de tela, símbolos, cabeçalhos) de PROG/CLAS/FUGR |
| Edição | `sap_set_text_elements` | Grava text elements — trava o objeto principal, escreve, libera e ativa |
| Transporte | `sap_transport_check` | Consulta o CTS por transporte(s) (TRKORR) disponível/recomendado para um objeto + pacote |
| Transporte | `sap_get_transport_requests` | Lista transportes (TRKORR) já existentes do usuário, opcionalmente filtrado por `target` |
| Auditoria | `sap_get_revisions` | Histórico de versões/revisões de um objeto (quem mudou, quando) |
| Auditoria | `sap_get_enhancements` | Lista enhancements ativos (BAdI/spot/ponto explícito) no código de um objeto |
| Auditoria | `sap_get_package_tree` | Lista sub-pacotes/objetos de um pacote (1 nível) — árvore do Project Explorer |
| Auditoria | `sap_get_short_dumps` | Lista short dumps recentes (ST22), com filtro `query` opcional |
| Refatoração | `sap_evaluate_rename` | Análise de impacto (só leitura) de renomear um identificador — não aplica a mudança |
| Refatoração | `sap_evaluate_extract_method` | Análise de impacto (só leitura) de extrair um trecho em método novo — não aplica a mudança |
| abapGit | `sap_abapgit_list_repos` | Lista repositórios abapGit vinculados a pacotes (somente leitura) |
| abapGit | `sap_abapgit_repo_status` | Diff staged/unstaged/ignored de um repositório (equivalente a `git status`) |
| Execução | `sap_execute_console` | Roda uma classe `IF_OO_ADT_CLASSRUN` e captura o output |
| Execução | `sap_sql_query` | Data Preview via Open SQL (`SELECT ... UP TO N ROWS`) |
| Qualidade | `sap_syntax_check` | Checagem de sintaxe sem precisar ativar |
| Qualidade | `sap_run_unit_test` | Executa testes ABAP Unit e traz o relatório |
| Qualidade | `sap_atc_run` | Roda o ABAP Test Cockpit (performance/segurança/clean code); faz polling do run assíncrono até terminar |
| Qualidade | `sap_atc_quickfix_evaluate` | Lista quickfixes disponíveis para um finding do ATC (via `quickfixInfo`) |
| Qualidade | `sap_atc_quickfix_apply` | Retorna a proposta de correção de um quickfix — não grava no SAP sozinho |
| Qualidade | `sap_get_migration_analysis` ⚠️ | Análise de migração; pode cair em fallback com dados fabricados (`mocked: true`) — ver [Status Conhecido](#-status-conhecido-limitações) |
| RAP130 Proxy | `abap_generators-list_generators` | Lista os geradores RAP conhecidos (smart proxy do RAP130) |
| RAP130 Proxy | `abap_generators-get_schema` | Schema de parâmetros de um gerador específico |
| RAP130 Proxy | `abap_generators-generate_objects` | **Intercepta** a geração em massa e instrui a IA a criar cada objeto individualmente via `sap_create_object` (trava de segurança deliberada) |
| Orquestradora | `sap_explain_object` | Junta `sap_object_structure` + `sap_get_source` (+ opcional `sap_usage_references`/`sap_get_enhancements`) num único retorno |
| Orquestradora | `sap_create_and_validate` | Mesmos parâmetros de `sap_create_object`, encadeando `sap_transport_check` → criação → `sap_syntax_check` → opcional `sap_atc_run` |
| Orquestradora | `sap_atc_autofix` | Encadeia `sap_atc_run` → `sap_atc_quickfix_evaluate`/`apply` por finding; só grava no SAP com `apply: true` explícito |
| Busca/Leitura | `sap_grep_object` | Busca por regex no fonte de um objeto ABAP, com contexto e suporte a includes (definitions/implementations/testclasses/macros) |
| Busca/Leitura | `sap_grep_package` | Busca por regex em todos os objetos de um pacote, via nodestructure |
| Busca/Leitura | `sap_search_repository` | Busca avançada no repositório (TADIR) por tipo/autor/pacote, além do nome |
| Busca/Leitura | `sap_find_definition` | "Ir para definição" — resolve o objeto/origem de um identificador numa posição do fonte |
| Busca/Leitura | `sap_element_info` | Detalhes de um elemento numa posição do fonte (tipo, declaração, documentação) |
| Busca/Leitura | `sap_type_hierarchy` | Hierarquia de tipos (supertipos/subtipos, implementações) de uma classe/interface |
| Busca/Leitura | `sap_object_types` | Lista os tipos de objeto suportados/conhecidos pelo ADT |
| Busca/Leitura | `sap_code_completion` | Sugestões de autocompletar numa posição do fonte (Ctrl+Space do ADT) |
| Busca/Leitura | `sap_discovery` | Lista os serviços/endpoints ADT disponíveis no sistema (discovery) |
| Edição | `sap_edit_source` | Edição cirúrgica por find-and-replace, sem reenviar o fonte inteiro (complementa `sap_replace_source_content`) |
| Edição | `sap_pretty_print` | Aplica o Pretty Printer (formatação padrão ADT) ao fonte |
| Transporte | `sap_create_transport` | Cria um novo transporte (TRKORR) — exige confirmação explícita |
| Transporte | `sap_transport_details` | Detalha o conteúdo de um transporte (objetos, tarefas, status) |
| Transporte | `sap_search_transports` | Busca transportes por filtros (E070/E071), além dos do próprio usuário |
| Execução | `sap_run_program` | Executa um programa/report ABAP e captura o resultado |
| Execução | `sap_execute_abap` | Executa código ABAP arbitrário via classe temporária em `$TMP` |
| Qualidade | `sap_syntax_check_source` | Checagem de sintaxe sobre um trecho de fonte enviado, sem precisar do objeto no SAP |
| Qualidade | `sap_compare_source` | Diff unificado (algoritmo LCS) entre dois objetos ABAP |
| Transação | `sap_create_transaction` | Cria um código de transação (TCODE) — exige confirmação explícita |
| Transação | `sap_delete_transaction` | Exclui um código de transação |
| Parâmetros | `sap_get_user_parameters` | Lê os parâmetros (SET/GET) do usuário SAP |
| Parâmetros | `sap_set_user_parameters` | Grava parâmetros (SET/GET) do usuário SAP |
| Orquestradora | `sap_workflow` | Orquestrador plan/build/test/review; com `confirmed=false` devolve o plano antes de executar |
| Debug | `ale_debug_master` | Debugger ABAP interativo via `/sap/bc/adt/debugger`. Ação única (`action`): `set_breakpoint` → `listen` (espera bater, em background; opcionalmente dispara a execução com `triggerType`/`triggerName`) → `status` (detecta o stop e faz attach) → `stack`/`variables` → `step`/`set_variable` → `stop`. Estado vive entre chamadas |

> Os nomes entre `/` na tabela acima (ex: `sap_activate` / `abap_activate-objects`) são duas ferramentas MCP registradas separadamente, mas que executam exatamente a mesma lógica — uma é só um alias de nome para compatibilidade com convenções diferentes de agente.

---

## 📋 Referência Técnica de Objetos

O **ALÊ ADT ABAP** mapeia os tipos técnicos do SAP (TADIR) para os identificadores de criação do ADT. Os templates XML foram validados contra a biblioteca de referência [abap-adt-api](https://github.com/marcellourbani/abap-adt-api) (usada em produção pelo `vscode-abap-remote-fs`). Códigos curtos como `CDS`, `TABLE`, `DOMAIN`, `FUNC` também são aceitos — veja `AdtTypeAlias`.

| Objeto SAP (TADIR) | `objtype` | Descrição | `parentName` é... | Fonte textual? |
|---|---|---|---|---|
| **R3TR PROG**  | `PROG/P`  | Programa Executável | Pacote | Sim |
| **R3TR PROG** (include) | `PROG/I` | Include de programa | Pacote | Sim |
| **R3TR CLAS**  | `CLAS/OC` | Classe Global | Pacote | Sim |
| **R3TR INTF**  | `INTF/OI` | Interface | Pacote | Sim |
| **R3TR FUGR**  | `FUGR/F`  | Grupo de Funções | Pacote | Não (container) |
| **R3TR FUGR** (módulo) | `FUGR/FF` | Módulo de Função | **Grupo de Funções** | Sim |
| **R3TR FUGR** (include) | `FUGR/I` | Include do Grupo de Funções | **Grupo de Funções** | Sim |
| **R3TR DDLS**  | `DDLS/DF` | Data Definition (CDS View) | Pacote | Sim |
| **R3TR DCLS**  | `DCLS/DL` | Access Control (DCL) | Pacote | Sim |
| **R3TR DDLX**  | `DDLX/EX` | Metadata Extension | Pacote | Sim |
| **R3TR DDLA**  | `DDLA/ADF`| Annotation Definition | Pacote | Sim |
| **R3TR TABL**  | `TABL/DT` | Tabela (DDL textual) | Pacote | Sim |
| **R3TR TABL** (estrutura) | `TABL/DS` | Estrutura (DDL textual) | Pacote | Sim |
| **R3TR DTEL**  | `DTEL/DE` | Elemento de Dados | Pacote | **Não — só shell, completar no Eclipse** |
| **R3TR DOMA**  | `DOMA/DD` | Domínio | Pacote | **Não — só shell, completar no Eclipse** |
| **R3TR MSAG**  | `MSAG/N`  | Classe de Mensagem | Pacote | **Não — só shell, completar no Eclipse** |
| **R3TR SRVD**  | `SRVD/SRV`| Service Definition | Pacote | Sim |
| **R3TR SRVB**  | `SRVB/SRV`| Service Binding | Pacote | Sem `/source/main` (objeto não é fonte textual), mas a criação já aceita `serviceDefinition`/`bindingType`/`bindingVersion`, confere a existência da Service Definition antes de criar e roda a validação prévia da AWS — não precisa completar no Eclipse |
| **R3TR BDEF**  | `BDEF/BO` | Behavior Definition | Pacote | Sim |

### ⚠️ Limitações de Plataforma

Os tipos abaixo **não foram implementados** porque não existe, em nenhuma fonte confiável (nem o `abap-adt-api`, nem documentação oficial da SAP, nem a comunidade), um endpoint REST/ADT ou um atributo XML validado para criá-los programaticamente:

- **Module Pool**: nem o shell do programa pode ser criado com confiança — o atributo que define o "tipo de programa" (TRDIR-SUBC = `M`, equivalente ao campo "Tipo" em SE38/Propriedades) não aparece em nenhum template de criação conhecido.
- **SmartForms** (transação SMARTFORMS) e **Adobe Forms** (layout via SFP/LiveCycle Designer): ferramentas gráficas exclusivas do SAP GUI/Eclipse, sem superfície REST pública conhecida.

Preferimos documentar essa limitação com transparência a entregar um template chutado que falharia silenciosamente em produção.

---

## 🧭 Status Conhecido (limitações)

- **`SRVD/SRV` e `BDEF/BO`**: podem retornar `HTTP 404` se o serviço ADT de RAP/ABAP Cloud não estiver ativo no backend (confirme via `GET /sap/bc/adt/discovery`).
- **`TABL/DS` (estrutura)**: exige a anotação `@AbapCatalog.enhancement.category : #NOT_EXTENSIBLE` no `initialSource`, ou o salvamento falha.
- **`sap_search_object`**: o campo `packageName` pode vir vazio para `DDLS/DF`.
- **`sap_sql_query`**: pode retornar 0 linhas sem erro em alguns ambientes, mesmo para tabelas não-vazias.
- **`sap_get_migration_analysis`**: cai em fallback com dados fabricados (`mocked: true`) se o endpoint não estiver disponível — sempre checar esse campo antes de usar o resultado.
- **Não implementado**: Module Pool, SmartForms, Adobe Forms (sem endpoint REST validado — ver [Limitações de Plataforma](#️-limitações-de-plataforma)); ABAP Debugger e traces de performance; abapGit `pull`/`push`/`create`/`unlink`; refatoração `preview`/`execute` (rename/extract method só fazem `evaluate`, somente leitura).

---

## 🤖 Agentes Claude Code prontos (`agents/`)

Este repositório inclui 5 subagentes de exemplo para [Claude Code](https://claude.com/claude-code), prontos para copiar para `~/.claude/agents/` — um port completo dos 5 agentes do [guidance-for-accelerating-sap-clean-core-journey-using-kiro-agents](https://github.com/aws-solutions-library-samples/guidance-for-accelerating-sap-clean-core-journey-using-kiro-agents) da AWS (originalmente para Kiro-CLI + um MCP server Python próprio), adaptados para rodar direto sobre as ferramentas deste MCP (Java/ADT):

| Agente daqui | Equivalente AWS | O que faz | Diferença chave |
|---|---|---|---|
| `agents/sap-clean-core-builder.md` | `abap-accelerator` | Cria/refatora objetos ABAP/RAP com Clean Core embutido | Usa `sap_transport_check`/`sap_get_transport_requests` antes de criar fora do `$TMP` e `sap_activate_batch` para dependência circular |
| `agents/sap-clean-core-checker.md` | `sap-atc-checker` | Compliance A-D via `sap_atc_run` | Aceita `variant`; trata findings vazios como não-verificado até confirmar nesta sessão que o polling funciona, depois trata como Level A real; usa `sap_get_migration_analysis` só sob pedido explícito, sempre checando o campo `mocked` antes de citar um achado |
| `agents/sap-custom-code-documenter.md` | `sap-custom-code-documenter` | Documentação dual-audiência (dev + negócio) | `sap_get_source` exige tipo composto (`CLAS/OC`), não tipo base como no original |
| `agents/sap-unused-code-discovery.md` | `sap-unused-code-discovery` | Candidatos a código morto | Sem dados SUSG (runtime) — só where-used estático (`sap_usage_references`), por isso confiança máxima é MEDIUM, nunca HIGH para "não usado" |
| `agents/sap-clean-core-business-mapper.md` | `business-function-mapper` | Resumo executivo por função de negócio | Propaga a ressalva de "não verificado" do checker para o resumo executivo, em vez de escondê-la |

Para usar: copie os arquivos `.md` para `~/.claude/agents/` (ou a pasta de agentes do seu projeto) e invoque pelo nome.

---

## 🗺️ Roadmap e Funcionalidades Futuras (To-Do)
- [x] **Análise de impacto de Refatoração**: `sap_evaluate_rename`/`sap_evaluate_extract_method`.
- [x] **Leitura de abapGit**: `sap_abapgit_list_repos`/`sap_abapgit_repo_status`.
- [ ] **Refatoração completa (preview/execute)**.
- [ ] **abapGit push/create/unlink**.
- [ ] **Module Pool / SmartForms / Adobe Forms** (ver [Limitações de Plataforma](#️-limitações-de-plataforma)).

---

## 🚀 Como Começar

### 1. Pré-requisitos
- Eclipse IDE (versão 2024-03 ou superior).
- **ABAP Development Tools (ADT)** instalado e configurado.
- Java 17+ instalado na máquina.
- Claude Code CLI instalado.

### 2. Instalação do Plugin

Para instalar diretamente no Eclipse (após ativar o GitHub Pages):

1. No Eclipse, vá em **Help ➡️ Install New Software...**.
2. Clique em **Add...**.
3. Preencha os campos:
   - **Name:** `ALÊ ADT ABAP`
   - **Location:** `https://333xandelz.github.io/ale_adt_abap/`
4. Selecione **ALÊ ADT ABAP**, clique em **Next ➡️ Finish** e reinicie o Eclipse.

> **⚠️ Importante:** Se o link acima não funcionar, verifique se você ativou o **GitHub Pages** nas configurações do seu repositório apontando para a pasta `/docs` na branch `main`.

### 3. Uso do Terminal
Acesse **Window ➡️ Show View ➡️ Other... ➡️ ALÊ ADT ABAP** para abrir o terminal de controle.
1. Conecte ao seu sistema SAP (URL, Usuário, Senha).
2. Clique em **Start Server** para subir o MCP na porta 3000.
3. No seu terminal favorito, digite `claude` e comece a desenvolver!

### 4. Atualizando para uma Versão Nova

Sempre que sair uma atualização (novas ferramentas, correções etc.):

1. No Eclipse, vá em **Help ➡️ Check for Updates**.
2. O Eclipse vai escanear o site de instalação (`https://333xandelz.github.io/ale_adt_abap/`) e listar a nova versão do **ALÊ ADT ABAP**.
3. Selecione, clique em **Next ➡️ Finish**, aceite a licença se solicitado, e reinicie o Eclipse quando pedido.
4. Depois de reiniciar, na view do plugin clique em **Stop Server** e depois **Start Server** de novo, para carregar as ferramentas novas no MCP.

> Se "Check for Updates" não encontrar nada novo, confirme que o **Location** configurado é exatamente `https://333xandelz.github.io/ale_adt_abap/` (Window ➡️ Preferences ➡️ Install/Update ➡️ Available Software Sites).

---

## 💻 Rodar Localmente sem Eclipse (Standalone)

Não quer abrir o Eclipse? O mesmo servidor MCP é empacotado como um **jar executável autônomo** (`ale-adt-mcp-standalone.jar`) que sobe fora do Eclipse e expõe **exatamente as mesmas ferramentas** — a lista é compartilhada via `com.sap.adt.mcp.tools.ToolRegistry`, então plugin e standalone nunca divergem. Ideal para **VS Code**, **Codex CLI**, **Claude Code**, **Cline** ou qualquer cliente MCP.

### Pré-requisitos
- Java 17+ (`java -version`).
- Um cliente MCP (VS Code, Codex CLI, Claude Code...). **Eclipse não é necessário.**

### Opção A — Script automático (recomendado)

O script faz tudo: valida o Java, lê as credenciais, baixa o jar mais recente do GitHub Releases (se não houver build local), configura o VS Code (`.vscode/mcp.json`) e o Codex CLI (`~/.codex/config.toml`), e sobe o servidor.

1. Crie o arquivo de credenciais a partir do exemplo:
   ```bash
   cp setup/.env.example setup/.env
   ```
2. Edite `setup/.env` com os dados do seu sistema SAP (`SAP_URL`, `SAP_USER`, `SAP_PASS` e, se precisar, `SAP_CLIENT`, `SAP_LANG`, `SAP_INSECURE_SSL`, `MCP_PORT`).
3. Suba o servidor:
   - **Windows (PowerShell):** `./setup/start.ps1`
   - **Linux/macOS:** `./setup/start.sh`

O servidor fica disponível em `http://localhost:3000/mcp` (porta configurável via `MCP_PORT`). `Ctrl+C` para parar.

> O `setup/.env` está no `.gitignore` — suas credenciais nunca vão para o repositório.

### Opção B — Manual (baixar o jar e rodar)

1. Baixe o jar da última release:
   `https://github.com/333xandelz/ale_adt_abap/releases/latest/download/ale-adt-mcp-standalone.jar`
2. Rode informando as credenciais por variáveis de ambiente:
   ```bash
   SAP_URL=https://host:44300 SAP_USER=ALEXANDRE SAP_PASS=*** SAP_CLIENT=100 SAP_LANG=PT \
     java -jar ale-adt-mcp-standalone.jar
   ```

| Variável | Obrigatória | Default | Descrição |
|---|---|---|---|
| `SAP_URL` | sim | — | URL do servidor ADT, ex.: `https://host:44300` |
| `SAP_USER` | sim | — | usuário SAP |
| `SAP_PASS` | sim | — | senha SAP |
| `SAP_CLIENT` | não | (default do sistema) | mandante, ex.: `100` |
| `SAP_LANG` | não | `EN` | idioma de logon, ex.: `PT` |
| `SAP_INSECURE_SSL` | não | `false` | `true` confia em qualquer certificado (dev) |
| `MCP_PORT` | não | `3000` | porta HTTP do MCP |

### Conectar o cliente MCP

O script já gera essas configs, mas se precisar fazer à mão:

**VS Code** — `.vscode/mcp.json` no seu workspace:
```json
{
  "servers": {
    "ale-adt": { "type": "http", "url": "http://localhost:3000/mcp" }
  }
}
```

**Codex CLI** — bloco em `~/.codex/config.toml`:
```toml
[mcp_servers.ale-adt]
url = "http://localhost:3000/mcp"
default_tools_approval_mode = "auto"
```

> As credenciais SAP ficam no ambiente onde o jar roda (ou no `setup/.env`), **nunca** no `mcp.json`/`config.toml`.

### Atualizar para uma versão nova

Rodando pela **Opção A**, basta apagar o jar em cache (`setup/ale-adt-mcp-standalone.jar`) e rodar o script de novo — ele baixa a release mais recente. Na **Opção B**, baixe o jar novo pelo link `releases/latest`. (Detalhes adicionais em [`com.sap.adt.mcp.server.standalone/README.md`](com.sap.adt.mcp.server.standalone/README.md).)

---

## 🛠️ Para Desenvolvedores (Git Clone & Build)

Se você deseja modificar o plugin ou contribuir para o projeto, pode clonar e compilar manualmente:

```bash
# Clone o repositório
git clone https://github.com/333xandelz/ale_adt_abap.git

# Entre na pasta
cd ale_adt_abap

# Compile o projeto (Gera o P2 Repository local)
mvn clean install
```
Após o build, você também pode instalar apontando o Eclipse para a pasta local:
`com.sap.adt.mcp.server.site/target/repository`

Para gerar apenas o **jar standalone** (sem Eclipse), que reaproveita as mesmas fontes do plugin:
```bash
mvn -f com.sap.adt.mcp.server.standalone/pom.xml clean package
```
O fat-jar sai em `com.sap.adt.mcp.server.standalone/target/ale-adt-mcp-standalone-2.0.0.jar`. Esse mesmo build é o que o workflow de release (`.github/workflows/release.yml`) executa a cada tag `vX.Y.Z`, publicando o jar em **GitHub Releases**.

---

## 📚 Créditos e Fontes Técnicas

| Fonte | Onde foi usada |
|---|---|
| [sap-adt-mcp-server](https://github.com/YahorNovik/sap-adt-mcp-server) (Yahor Novik) | Base original deste projeto. |
| [abap-adt-api](https://github.com/marcellourbani/abap-adt-api) (Marcello Urbani) | Templates XML/`Content-Type` da [Referência Técnica de Objetos](#-referência-técnica-de-objetos); ferramentas de auditoria, refatoração e abapGit. |
| [mcp-abap-adt](https://github.com/fr0ster/mcp-abap-adt) (fr0ster) | Inspiração para `sap_get_short_dumps`. |
| [SAP ABAP Accelerator da AWS](https://github.com/aws-solutions-library-samples/guidance-for-deploying-sap-abap-accelerator-for-amazon-q-developer) | `sap_transport_check`, ATC quickfixes, `sap_activate_batch`, `sap_get_transport_requests`, `sap_get_migration_analysis`, `sap_get_test_classes`/`sap_create_or_update_test_class`, pré-checagem de `SRVB/SRV`. |
| [guidance-for-accelerating-sap-clean-core-journey-using-kiro-agents](https://github.com/aws-solutions-library-samples/guidance-for-accelerating-sap-clean-core-journey-using-kiro-agents) (AWS) | Arquitetura e escopo dos 5 subagentes em [`agents/`](agents/). |
| [SAP RAP130](https://github.com/SAP-samples/abap-platform-rap130) | Workflow de `abap_generators-*`/`abap_creation-*` e o `agent.md`. |

---

## 📜 Licença

Distribuído sob a licença Apache 2.0. Veja `LICENSE` para mais informações.

---
*Desenvolvido com ❤️ para a comunidade ABAP.*
