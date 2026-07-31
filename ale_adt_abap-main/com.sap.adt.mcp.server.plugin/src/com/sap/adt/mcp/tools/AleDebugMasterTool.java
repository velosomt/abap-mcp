package com.sap.adt.mcp.tools;

import java.io.StringReader;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.sap.adt.mcp.sap.AdtRestClient;

/**
 * Tool: <b>ale_debug_master</b> — debugger interativo do ABAP via os endpoints REST
 * que a SAP expõe em {@code /sap/bc/adt/debugger/*}.
 *
 * <p>Uma única tool com parâmetro {@code action}. O estado da sessão de debug é
 * mantido entre chamadas no {@link DebugSessionManager} (singleton), porque o
 * protocolo do debugger é uma conversa stateful: registrar listener (long-poll em
 * background) → breakpoint atingido → attach → step/stack/variables → stop. A mesma
 * sessão HTTP (cookies/CSRF do {@link AdtRestClient}) é reaproveitada em todas as
 * chamadas, o que é o que liga o listener ao attach.</p>
 *
 * <p>Modos de disparo (como o código chega ao breakpoint):
 * <ul>
 *   <li><b>externo</b>: você roda a transação/report/serviço no SAP; a tool só
 *       registra o breakpoint, escuta e inspeciona.</li>
 *   <li><b>autônomo</b>: passe {@code triggerType}+{@code triggerName} na ação
 *       {@code listen} e a tool dispara a execução numa thread separada.</li>
 * </ul></p>
 *
 * <p>Payloads/endpoints portados do projeto MIT
 * <a href="https://github.com/marcellourbani/abap-adt-api">abap-adt-api</a>
 * (já creditado no README). Como o protocolo do debugger não é documentado pela SAP,
 * cada ação inclui no retorno o corpo XML bruto ({@code raw*}) para depuração ao vivo.</p>
 */
public class AleDebugMasterTool extends AbstractMcpTool {

    public static final String NAME = "ale_debug_master";

    private static final String DBG = "/sap/bc/adt/debugger";
    private static final Duration LISTEN_TIMEOUT = Duration.ofHours(2);
    private static final Duration STEP_TIMEOUT = Duration.ofMinutes(30);

    private final DebugSessionManager manager = DebugSessionManager.getInstance();

    public AleDebugMasterTool(AdtRestClient client) {
        super(client);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return "Debugger ABAP interativo via /sap/bc/adt/debugger. Acao unica com parametro 'action': "
                + "new_session, set_breakpoint, listen, status, stack, variables, step, set_variable, stop. "
                + "Fluxo: set_breakpoint -> listen (espera bater, em background; opcionalmente dispara a execucao "
                + "com triggerType/triggerName) -> status (detecta o stop e faz attach) -> stack/variables -> "
                + "step (stepInto/stepOver/stepReturn/stepContinue) / set_variable -> stop. O estado vive entre "
                + "chamadas; nao e preciso repetir o sessionId quando ha uma so sessao.";
    }

    @Override
    public JsonObject getInputSchema() {
        JsonObject properties = new JsonObject();

        JsonObject action = new JsonObject();
        action.addProperty("type", "string");
        JsonArray actionEnum = new JsonArray();
        for (String a : new String[]{"new_session", "set_breakpoint", "listen", "status",
                "stack", "variables", "step", "set_variable", "stop"}) {
            actionEnum.add(a);
        }
        action.add("enum", actionEnum);
        action.addProperty("description", "Operacao a executar.");
        properties.add("action", action);

        properties.add("sessionId", strProp("Id da sessao de debug. Opcional quando ha uma unica sessao ativa."));
        properties.add("debuggingMode", strProp("'user' (default, anexa a qualquer sessao do usuario) ou 'terminal'."));

        // breakpoint targeting
        properties.add("objectType", strProp("Tipo do objeto (ex: PROG, CLAS) para resolver a URL do fonte."));
        properties.add("objectName", strProp("Nome do objeto (ex: ZMY_REPORT)."));
        JsonObject line = new JsonObject();
        line.addProperty("type", "integer");
        line.addProperty("description", "Numero da linha do breakpoint (1-based).");
        properties.add("line", line);
        properties.add("breakpointUri", strProp("URI ADT completa do breakpoint, ex: "
                + "/sap/bc/adt/programs/programs/ZFOO/source/main#start=10 (alternativa a objectName+line)."));
        JsonObject bps = new JsonObject();
        bps.addProperty("type", "array");
        JsonObject bpsItems = new JsonObject();
        bpsItems.addProperty("type", "string");
        bps.add("items", bpsItems);
        bps.addProperty("description", "Lista de URIs de breakpoint (para varios de uma vez).");
        properties.add("breakpoints", bps);

        // trigger (autonomous mode)
        properties.add("triggerType", strProp("Disparo autonomo na acao listen: 'program' (programrun), "
                + "'class' (classrun/IF_OO_ADT_CLASSRUN). Omita para disparo externo."));
        properties.add("triggerName", strProp("Nome do programa/classe a executar no disparo autonomo."));

        // inspection / stepping
        JsonObject vars = new JsonObject();
        vars.addProperty("type", "array");
        JsonObject varsItems = new JsonObject();
        varsItems.addProperty("type", "string");
        vars.add("items", varsItems);
        vars.addProperty("description", "IDs de variaveis a ler (action=variables). Use parentId para drill-down.");
        properties.add("variables", vars);
        properties.add("parentId", strProp("ID de variavel pai para ler os filhos (action=variables, drill-down)."));
        properties.add("variableName", strProp("Nome da variavel a alterar (action=set_variable)."));
        properties.add("value", strProp("Novo valor da variavel (action=set_variable)."));

        JsonObject stepType = new JsonObject();
        stepType.addProperty("type", "string");
        JsonArray stepEnum = new JsonArray();
        for (String s : new String[]{"stepInto", "stepOver", "stepReturn", "stepContinue",
                "stepRunToLine", "stepJumpToLine", "terminateDebuggee"}) {
            stepEnum.add(s);
        }
        stepType.add("enum", stepEnum);
        stepType.addProperty("description", "Tipo de step (action=step). Default: stepOver.");
        properties.add("stepType", stepType);
        properties.add("stepUri", strProp("URI alvo para stepRunToLine/stepJumpToLine."));

        JsonArray required = new JsonArray();
        required.add("action");

        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.add("properties", properties);
        schema.add("required", required);
        return schema;
    }

    @Override
    public String execute(JsonObject args) throws Exception {
        String action = optString(args, "action");
        if (action == null || action.isEmpty()) {
            return error("Informe 'action'.");
        }
        switch (action) {
            case "new_session":   return handleNewSession(args);
            case "set_breakpoint":return handleSetBreakpoint(args);
            case "listen":        return handleListen(args);
            case "status":        return handleStatus(args);
            case "stack":         return handleStack(args);
            case "variables":     return handleVariables(args);
            case "step":          return handleStep(args);
            case "set_variable":  return handleSetVariable(args);
            case "stop":          return handleStop(args);
            default:              return error("Acao desconhecida: " + action);
        }
    }

    // ------------------------------------------------------------------ actions

    private String handleNewSession(JsonObject args) {
        DebugSession s = createSession(args);
        JsonObject out = base(s);
        out.addProperty("status", "created");
        out.addProperty("message", "Sessao de debug criada. Use action=set_breakpoint e depois action=listen.");
        return out.toString();
    }

    private String handleSetBreakpoint(JsonObject args) throws Exception {
        DebugSession s = resolveOrCreate(args);
        List<String> uris = resolveBreakpointUris(args);
        if (uris.isEmpty()) {
            return error("Nenhum breakpoint informado. Use objectName+line, breakpointUri ou breakpoints[].");
        }

        StringBuilder bpXml = new StringBuilder();
        for (String uri : uris) {
            bpXml.append("<breakpoint xmlns:adtcore=\"http://www.sap.com/adt/core\" kind=\"line\" clientId=\"")
                 .append(UUID.randomUUID().toString())
                 .append("\" skipCount=\"0\" adtcore:uri=\"")
                 .append(escapeXml(uri))
                 .append("\"/>");
        }

        String body = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<dbg:breakpoints scope=\"external\" debuggingMode=\"" + s.debuggingMode + "\""
                + " requestUser=\"" + escapeXml(s.requestUser) + "\""
                + " terminalId=\"" + s.terminalId + "\" ideId=\"" + s.ideId + "\""
                + " systemDebugging=\"false\" deactivated=\"false\""
                + " xmlns:dbg=\"http://www.sap.com/adt/debugger\">"
                + bpXml
                + "</dbg:breakpoints>";

        HttpResponse<String> resp = client.postWithHeaders(DBG + "/breakpoints", body,
                "application/xml", "application/xml", STATEFUL_HEADERS);

        s.setBreakpointCount(s.getBreakpointCount() + uris.size());

        JsonObject out = base(s);
        out.addProperty("status", "breakpoints_set");
        out.addProperty("httpStatus", resp.statusCode());
        JsonArray arr = new JsonArray();
        uris.forEach(arr::add);
        out.add("breakpoints", arr);
        out.addProperty("rawResponse", truncate(resp.body()));
        out.addProperty("message", "Breakpoints registrados. Agora chame action=listen.");
        return out.toString();
    }

    private String handleListen(JsonObject args) {
        DebugSession s = resolveOrCreate(args);
        if (s.getState() == DebugSession.State.LISTENING) {
            return error("Sessao ja esta escutando (LISTENING). Use action=status.");
        }

        final String mode = s.debuggingMode;
        final String user = s.requestUser;
        final String path = DBG + "/listeners?debuggingMode=" + mode
                + "&requestUser=" + urlEncode(user)
                + "&terminalId=" + s.terminalId
                + "&ideId=" + s.ideId
                + "&checkConflict=true&isNotifiedOnConflict=true";

        Thread listener = new Thread(() -> {
            try {
                // Accept must be application/vnd.sap.as+xml -- this listener endpoint answers
                // HTTP 406 "Accepted content types: application/vnd.sap.as+xml" for plain
                // application/xml, unlike /breakpoints which accepts application/xml fine.
                HttpResponse<String> resp = client.postWithHeaders(path, "",
                        "application/xml", "application/vnd.sap.as+xml", STATEFUL_HEADERS, LISTEN_TIMEOUT);
                String bodyResp = resp.body();
                s.setLastListenerBody(bodyResp);
                String debuggeeId = firstLocalText(bodyResp, "DEBUGGEE_ID");
                if (debuggeeId != null && !debuggeeId.isEmpty()) {
                    s.setDebuggeeId(debuggeeId);
                    s.setState(DebugSession.State.REACHED);
                } else {
                    s.setState(DebugSession.State.ERROR);
                    s.setLastError("Listener retornou sem DEBUGGEE_ID (possivel conflito ou nenhum breakpoint). "
                            + "Veja rawListener.");
                }
            } catch (Exception e) {
                s.setLastError(e.getMessage());
                s.setState(DebugSession.State.ERROR);
            }
        }, "ale-debug-listener-" + s.sessionId);
        listener.setDaemon(true);
        s.setListenerThread(listener);
        s.setState(DebugSession.State.LISTENING);
        listener.start();

        // Disparo autonomo opcional
        String triggerType = optString(args, "triggerType");
        String triggerName = optString(args, "triggerName");
        boolean triggered = false;
        if (triggerType != null && triggerName != null && !triggerName.isEmpty()) {
            startTrigger(s, triggerType, triggerName.toUpperCase());
            triggered = true;
        }

        JsonObject out = base(s);
        out.addProperty("status", "listening");
        out.addProperty("autonomousTrigger", triggered);
        out.addProperty("message", triggered
                ? "Listener ativo e execucao disparada. Faca polling com action=status."
                : "Listener ativo. Dispare a execucao no SAP e faca polling com action=status.");
        return out.toString();
    }

    private String handleStatus(JsonObject args) throws Exception {
        DebugSession s = require(args);
        if (s == null) {
            return error("Nenhuma sessao de debug. Use action=new_session/set_breakpoint/listen.");
        }

        // Auto-attach na primeira vez que detecta o stop
        if (s.getState() == DebugSession.State.REACHED && !s.isAttached()) {
            attach(s);
        }

        JsonObject out = base(s);
        out.addProperty("state", s.getState().name());
        out.addProperty("attached", s.isAttached());
        if (s.getDebuggeeId() != null) {
            out.addProperty("debuggeeId", s.getDebuggeeId());
        }
        if (s.getLastError() != null) {
            out.addProperty("error", s.getLastError());
        }
        if (s.getLastListenerBody() != null) {
            out.addProperty("rawListener", truncate(s.getLastListenerBody()));
        }
        if (s.getLastAttachBody() != null) {
            out.add("reachedBreakpoints", parseReachedBreakpoints(s.getLastAttachBody()));
            out.addProperty("rawAttach", truncate(s.getLastAttachBody()));
        }
        return out.toString();
    }

    private String handleStack(JsonObject args) throws Exception {
        DebugSession s = requireAttached(args);
        if (s == null) {
            return error("Sessao nao anexada. Aguarde o breakpoint (action=status) antes de pedir a stack.");
        }
        HttpResponse<String> resp = client.getWithHeaders(DBG + "/stack?method=getStack&emode=_&semanticURIs=true",
                "application/xml", STATEFUL_HEADERS);
        JsonObject out = base(s);
        out.addProperty("status", "ok");
        out.add("stack", parseStack(resp.body()));
        out.addProperty("raw", truncate(resp.body()));
        return out.toString();
    }

    private String handleVariables(JsonObject args) throws Exception {
        DebugSession s = requireAttached(args);
        if (s == null) {
            return error("Sessao nao anexada.");
        }

        String parentId = optString(args, "parentId");
        HttpResponse<String> resp;
        if (parentId != null && !parentId.isEmpty()) {
            String body = "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>"
                    + "<asx:abap version=\"1.0\" xmlns:asx=\"http://www.sap.com/abapxml\"><asx:values><DATA>"
                    + "<HIERARCHIES><STPDA_ADT_VARIABLE_HIERARCHY><PARENT_ID>"
                    + escapeXml(parentId)
                    + "</PARENT_ID></STPDA_ADT_VARIABLE_HIERARCHY></HIERARCHIES>"
                    + "</DATA></asx:values></asx:abap>";
            resp = client.postWithHeaders(DBG + "?method=getChildVariables", body,
                    "application/vnd.sap.as+xml; charset=UTF-8; dataname=com.sap.adt.debugger.ChildVariables",
                    "application/vnd.sap.as+xml;charset=UTF-8;dataname=com.sap.adt.debugger.ChildVariables",
                    STATEFUL_HEADERS);
        } else {
            StringBuilder ids = new StringBuilder();
            if (args.has("variables") && args.get("variables").isJsonArray()) {
                for (JsonElement e : args.getAsJsonArray("variables")) {
                    ids.append("<STPDA_ADT_VARIABLE><ID>")
                       .append(escapeXml(e.getAsString()))
                       .append("</ID></STPDA_ADT_VARIABLE>");
                }
            }
            String body = "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>"
                    + "<asx:abap xmlns:asx=\"http://www.sap.com/abapxml\" version=\"1.0\"><asx:values>"
                    + "<DATA>" + ids + "</DATA></asx:values></asx:abap>";
            resp = client.postWithHeaders(DBG + "?method=getVariables", body,
                    "application/vnd.sap.as+xml; charset=UTF-8; dataname=com.sap.adt.debugger.Variables",
                    "application/vnd.sap.as+xml;charset=UTF-8;dataname=com.sap.adt.debugger.Variables",
                    STATEFUL_HEADERS);
        }

        JsonObject out = base(s);
        out.addProperty("status", "ok");
        out.add("variables", parseVariables(resp.body()));
        out.addProperty("raw", truncate(resp.body()));
        return out.toString();
    }

    private String handleStep(JsonObject args) throws Exception {
        DebugSession s = requireAttached(args);
        if (s == null) {
            return error("Sessao nao anexada.");
        }
        String stepType = optString(args, "stepType");
        if (stepType == null || stepType.isEmpty()) {
            stepType = "stepOver";
        }
        String path = DBG + "?method=" + urlEncode(stepType);
        String stepUri = optString(args, "stepUri");
        if (stepUri != null && !stepUri.isEmpty()) {
            path += "&uri=" + urlEncode(stepUri);
        }

        HttpResponse<String> resp = client.postWithHeaders(path, "",
                "application/xml", "application/xml", STATEFUL_HEADERS, STEP_TIMEOUT);

        if ("terminateDebuggee".equals(stepType) || "stepContinue".equals(stepType)) {
            // pode ter encerrado/seguido o debuggee; o estado real e relido via status/stack
            s.setAttached(false);
            s.setState(DebugSession.State.IDLE);
        }

        JsonObject out = base(s);
        out.addProperty("status", "stepped");
        out.addProperty("stepType", stepType);
        out.addProperty("httpStatus", resp.statusCode());
        out.addProperty("raw", truncate(resp.body()));
        out.addProperty("message", "stepContinue/terminateDebuggee podem soltar o debuggee; "
                + "chame action=stack/variables para ver o novo ponto.");
        return out.toString();
    }

    private String handleSetVariable(JsonObject args) throws Exception {
        DebugSession s = requireAttached(args);
        if (s == null) {
            return error("Sessao nao anexada.");
        }
        String name = optString(args, "variableName");
        String value = optString(args, "value");
        if (name == null || name.isEmpty()) {
            return error("Informe 'variableName'.");
        }
        if (value == null) {
            value = "";
        }
        String path = DBG + "?method=setVariableValue&variableName=" + urlEncode(name);
        HttpResponse<String> resp = client.postWithHeaders(path, value,
                "text/plain; charset=UTF-8", "application/xml", STATEFUL_HEADERS, Duration.ofSeconds(60));

        JsonObject out = base(s);
        out.addProperty("status", "variable_set");
        out.addProperty("variableName", name);
        out.addProperty("httpStatus", resp.statusCode());
        out.addProperty("raw", truncate(resp.body()));
        return out.toString();
    }

    private String handleStop(JsonObject args) {
        DebugSession s = require(args);
        if (s == null) {
            return error("Nenhuma sessao para encerrar.");
        }

        // 1. tenta terminar o debuggee, se anexado
        if (s.isAttached() && s.getDebuggeeId() != null) {
            try {
                client.postWithHeaders(DBG + "?method=terminateDebuggee", "",
                        "application/xml", "application/xml", STATEFUL_HEADERS, Duration.ofSeconds(30));
            } catch (Exception ignore) {
                // best-effort
            }
        }

        // 2. remove o listener
        String delPath = DBG + "/listeners?debuggingMode=" + s.debuggingMode
                + "&requestUser=" + urlEncode(s.requestUser)
                + "&terminalId=" + s.terminalId
                + "&ideId=" + s.ideId
                + "&checkConflict=false&notifyConflict=true";
        String delMsg = "ok";
        try {
            client.deleteWithHeaders(delPath, STATEFUL_HEADERS);
        } catch (Exception e) {
            delMsg = "falha ao remover listener: " + e.getMessage();
        }

        // 3. interrompe threads e limpa
        interrupt(s.getListenerThread());
        interrupt(s.getTriggerThread());
        s.setAttached(false);
        s.setState(DebugSession.State.STOPPED);
        manager.remove(s.sessionId);

        JsonObject out = base(s);
        out.addProperty("status", "stopped");
        out.addProperty("listenerDelete", delMsg);
        return out.toString();
    }

    // ------------------------------------------------------------------ helpers

    private void attach(DebugSession s) throws Exception {
        String path = DBG + "?method=attach&debuggeeId=" + urlEncode(s.getDebuggeeId())
                + "&debuggingMode=" + s.debuggingMode
                + "&requestUser=" + urlEncode(s.requestUser);
        HttpResponse<String> resp = client.postWithHeaders(path, "", "application/xml", "application/xml",
                STATEFUL_HEADERS);
        s.setLastAttachBody(resp.body());
        s.setAttached(true);
        s.setState(DebugSession.State.ATTACHED);
    }

    private void startTrigger(DebugSession s, String triggerType, String triggerName) {
        Thread trigger = new Thread(() -> {
            try {
                // pequeno atraso para garantir que o listener ja esta registrado
                Thread.sleep(800);
                String path;
                if ("class".equalsIgnoreCase(triggerType) || "console".equalsIgnoreCase(triggerType)) {
                    path = "/sap/bc/adt/oo/classrun/" + triggerName;
                } else {
                    path = "/sap/bc/adt/programs/programrun/" + triggerName;
                }
                // bloqueia ate o programa terminar (ou ate o debug ser solto); roda em background
                client.postWithHeaders(path, "", "application/xml", "text/plain", null, STEP_TIMEOUT);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                // a execucao costuma "falhar"/encerrar quando o debuggee e terminado; ignoramos
                System.err.println("ale_debug_master: trigger " + triggerName + " encerrou: " + e.getMessage());
            }
        }, "ale-debug-trigger-" + s.sessionId);
        trigger.setDaemon(true);
        s.setTriggerThread(trigger);
        trigger.start();
    }

    private DebugSession createSession(JsonObject args) {
        String mode = optString(args, "debuggingMode");
        if (mode == null || mode.isEmpty()) {
            mode = "user";
        }
        String user = client.getUsername();
        if (user != null) {
            user = user.toUpperCase();
        }
        String sid = "dbg-" + Long.toString(System.currentTimeMillis() % 1000000L);
        String terminalId = UUID.randomUUID().toString().toUpperCase().replace("-", "");
        String ideId = UUID.randomUUID().toString().toUpperCase().replace("-", "");
        DebugSession s = new DebugSession(sid, user, terminalId, ideId, mode);
        manager.put(s);
        return s;
    }

    private DebugSession resolveOrCreate(JsonObject args) {
        DebugSession s = manager.get(optString(args, "sessionId"));
        return s != null ? s : createSession(args);
    }

    private DebugSession require(JsonObject args) {
        return manager.get(optString(args, "sessionId"));
    }

    private DebugSession requireAttached(JsonObject args) throws Exception {
        DebugSession s = require(args);
        if (s == null) {
            return null;
        }
        if (s.getState() == DebugSession.State.REACHED && !s.isAttached()) {
            attach(s);
        }
        return s.isAttached() ? s : null;
    }

    private List<String> resolveBreakpointUris(JsonObject args) {
        List<String> uris = new ArrayList<>();
        if (args.has("breakpoints") && args.get("breakpoints").isJsonArray()) {
            for (JsonElement e : args.getAsJsonArray("breakpoints")) {
                if (!e.isJsonNull()) {
                    uris.add(e.getAsString());
                }
            }
        }
        String bpUri = optString(args, "breakpointUri");
        if (bpUri != null && !bpUri.isEmpty()) {
            uris.add(bpUri);
        }
        String objName = optString(args, "objectName");
        if (objName != null && !objName.isEmpty()) {
            String type = optString(args, "objectType");
            String src = AdtUrlResolver.resolveSourceUrl(type, objName);
            if (src == null) {
                src = optString(args, "objectUri");
            }
            src = ensureSourceUrl(src);
            if (src != null && !src.isEmpty()) {
                if (args.has("line") && !args.get("line").isJsonNull()) {
                    uris.add(src + "#start=" + args.get("line").getAsInt());
                } else {
                    uris.add(src);
                }
            }
        }
        return uris;
    }

    private JsonObject base(DebugSession s) {
        JsonObject o = new JsonObject();
        o.addProperty("tool", NAME);
        o.addProperty("sessionId", s.sessionId);
        o.addProperty("requestUser", s.requestUser);
        o.addProperty("debuggingMode", s.debuggingMode);
        return o;
    }

    private static JsonObject strProp(String desc) {
        JsonObject p = new JsonObject();
        p.addProperty("type", "string");
        p.addProperty("description", desc);
        return p;
    }

    private static String error(String msg) {
        JsonObject o = new JsonObject();
        o.addProperty("tool", NAME);
        o.addProperty("status", "error");
        o.addProperty("error", msg);
        return o.toString();
    }

    private static void interrupt(Thread t) {
        if (t != null && t.isAlive()) {
            t.interrupt();
        }
    }

    private static String truncate(String s) {
        if (s == null) {
            return null;
        }
        return s.length() > 4000 ? s.substring(0, 4000) + "...[truncado]" : s;
    }

    // ------------------------------------------------------------------ XML parsing (lenient, by local name)

    private static org.w3c.dom.Document parseXml(String xml) {
        if (xml == null || xml.isBlank()) {
            return null;
        }
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setNamespaceAware(false);
            DocumentBuilder b = f.newDocumentBuilder();
            return b.parse(new InputSource(new StringReader(xml)));
        } catch (Exception e) {
            return null;
        }
    }

    private static String localName(Node n) {
        String tag = n.getNodeName();
        int idx = tag.indexOf(':');
        return idx >= 0 ? tag.substring(idx + 1) : tag;
    }

    private static void collectByLocal(Node node, String local, List<Element> out) {
        NodeList ch = node.getChildNodes();
        for (int i = 0; i < ch.getLength(); i++) {
            Node c = ch.item(i);
            if (c.getNodeType() == Node.ELEMENT_NODE) {
                if (localName(c).equalsIgnoreCase(local)) {
                    out.add((Element) c);
                }
                collectByLocal(c, local, out);
            }
        }
    }

    private static List<Element> elementsByLocal(String xml, String local) {
        List<Element> out = new ArrayList<>();
        org.w3c.dom.Document doc = parseXml(xml);
        if (doc != null && doc.getDocumentElement() != null) {
            Element root = doc.getDocumentElement();
            if (localName(root).equalsIgnoreCase(local)) {
                out.add(root);
            }
            collectByLocal(root, local, out);
        }
        return out;
    }

    private static String firstLocalText(String xml, String local) {
        List<Element> els = elementsByLocal(xml, local);
        return els.isEmpty() ? null : els.get(0).getTextContent();
    }

    private static String attrByLocal(Element el, String local) {
        org.w3c.dom.NamedNodeMap attrs = el.getAttributes();
        for (int i = 0; i < attrs.getLength(); i++) {
            Node a = attrs.item(i);
            if (localName(a).equalsIgnoreCase(local)) {
                return a.getNodeValue();
            }
        }
        return "";
    }

    private JsonArray parseStack(String xml) {
        JsonArray arr = new JsonArray();
        for (Element e : elementsByLocal(xml, "stackEntry")) {
            JsonObject o = new JsonObject();
            o.addProperty("uri", attrByLocal(e, "uri"));
            o.addProperty("type", attrByLocal(e, "type"));
            o.addProperty("name", attrByLocal(e, "name"));
            o.addProperty("programName", attrByLocal(e, "programName"));
            o.addProperty("line", attrByLocal(e, "line"));
            arr.add(o);
        }
        return arr;
    }

    private JsonArray parseVariables(String xml) {
        JsonArray arr = new JsonArray();
        for (Element e : elementsByLocal(xml, "STPDA_ADT_VARIABLE")) {
            JsonObject o = new JsonObject();
            addChildText(e, o, "ID");
            addChildText(e, o, "NAME");
            addChildText(e, o, "VALUE");
            addChildText(e, o, "DECLARED_TYPE_NAME");
            addChildText(e, o, "KIND");
            if (o.size() > 0) {
                arr.add(o);
            }
        }
        return arr;
    }

    private static void addChildText(Element parent, JsonObject o, String childLocal) {
        NodeList ch = parent.getChildNodes();
        for (int i = 0; i < ch.getLength(); i++) {
            Node c = ch.item(i);
            if (c.getNodeType() == Node.ELEMENT_NODE && localName(c).equalsIgnoreCase(childLocal)) {
                o.addProperty(childLocal, c.getTextContent());
                return;
            }
        }
    }

    private JsonArray parseReachedBreakpoints(String attachXml) {
        JsonArray arr = new JsonArray();
        for (Element e : elementsByLocal(attachXml, "breakpoint")) {
            JsonObject o = new JsonObject();
            o.addProperty("id", attrByLocal(e, "id"));
            o.addProperty("kind", attrByLocal(e, "kind"));
            arr.add(o);
        }
        return arr;
    }
}
