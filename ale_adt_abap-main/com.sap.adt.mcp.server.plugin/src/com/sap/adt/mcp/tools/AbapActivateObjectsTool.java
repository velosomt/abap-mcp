package com.sap.adt.mcp.tools;
import com.sap.adt.mcp.sap.AdtRestClient;

public class AbapActivateObjectsTool extends ActivateTool {
    public AbapActivateObjectsTool(AdtRestClient client) { super(client); }
    @Override public String getName() { return "abap_activate-objects"; }
}
