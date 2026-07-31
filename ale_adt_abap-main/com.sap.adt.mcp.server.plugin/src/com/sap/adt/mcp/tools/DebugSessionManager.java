package com.sap.adt.mcp.tools;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registro singleton das sessões de debug ativas do {@link AleDebugMasterTool}.
 *
 * <p>Mantém um mapa sessionId → {@link DebugSession} e a noção de "sessão atual",
 * para que as ações subsequentes (status/stack/variables/step/stop) não precisem
 * repetir o sessionId quando há apenas uma sessão em andamento.</p>
 */
public final class DebugSessionManager {

    private static final DebugSessionManager INSTANCE = new DebugSessionManager();

    private final Map<String, DebugSession> sessions = new ConcurrentHashMap<>();
    private volatile String currentSessionId;

    private DebugSessionManager() {
    }

    public static DebugSessionManager getInstance() {
        return INSTANCE;
    }

    public synchronized void put(DebugSession s) {
        sessions.put(s.sessionId, s);
        currentSessionId = s.sessionId;
    }

    public DebugSession get(String id) {
        String key = (id == null || id.isEmpty()) ? currentSessionId : id;
        return key == null ? null : sessions.get(key);
    }

    public synchronized DebugSession remove(String id) {
        String key = (id == null || id.isEmpty()) ? currentSessionId : id;
        if (key == null) {
            return null;
        }
        DebugSession s = sessions.remove(key);
        if (key.equals(currentSessionId)) {
            currentSessionId = null;
        }
        return s;
    }

    public String currentId() {
        return currentSessionId;
    }

    public int count() {
        return sessions.size();
    }
}
