package com.sap.adt.mcp.tools;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Estado de uma sessão de debug do {@link AleDebugMasterTool}, mantido VIVO entre
 * chamadas MCP (as tools são instâncias de vida longa no servidor).
 *
 * <p>O ciclo: IDLE → (set_breakpoint) → LISTENING (listener em background) →
 * REACHED (breakpoint atingido) → ATTACHED (anexado ao debuggee) → STOPPED.
 * ERROR em qualquer falha. Campos voláteis porque são lidos/escritos por threads
 * distintas (a thread do listener e a thread da requisição MCP).</p>
 */
public class DebugSession {

    public enum State { IDLE, LISTENING, REACHED, ATTACHED, STOPPED, ERROR }

    public final String sessionId;
    public final String requestUser;
    public final String terminalId;
    public final String ideId;
    public final String debuggingMode; // "user" | "terminal"

    private final AtomicReference<State> state = new AtomicReference<>(State.IDLE);

    private volatile String debuggeeId;
    private volatile boolean attached;
    private volatile String lastError;
    private volatile String lastListenerBody;
    private volatile String lastAttachBody;
    private volatile Thread listenerThread;
    private volatile Thread triggerThread;
    private volatile int breakpointCount;

    public DebugSession(String sessionId, String requestUser, String terminalId,
                        String ideId, String debuggingMode) {
        this.sessionId = sessionId;
        this.requestUser = requestUser;
        this.terminalId = terminalId;
        this.ideId = ideId;
        this.debuggingMode = debuggingMode;
    }

    public State getState() { return state.get(); }
    public void setState(State s) { state.set(s); }

    public String getDebuggeeId() { return debuggeeId; }
    public void setDebuggeeId(String v) { this.debuggeeId = v; }

    public boolean isAttached() { return attached; }
    public void setAttached(boolean v) { this.attached = v; }

    public String getLastError() { return lastError; }
    public void setLastError(String v) { this.lastError = v; }

    public String getLastListenerBody() { return lastListenerBody; }
    public void setLastListenerBody(String v) { this.lastListenerBody = v; }

    public String getLastAttachBody() { return lastAttachBody; }
    public void setLastAttachBody(String v) { this.lastAttachBody = v; }

    public Thread getListenerThread() { return listenerThread; }
    public void setListenerThread(Thread t) { this.listenerThread = t; }

    public Thread getTriggerThread() { return triggerThread; }
    public void setTriggerThread(Thread t) { this.triggerThread = t; }

    public int getBreakpointCount() { return breakpointCount; }
    public void setBreakpointCount(int v) { this.breakpointCount = v; }
}
