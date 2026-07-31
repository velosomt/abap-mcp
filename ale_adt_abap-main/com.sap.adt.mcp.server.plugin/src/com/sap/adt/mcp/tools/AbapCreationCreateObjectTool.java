package com.sap.adt.mcp.tools;
import com.sap.adt.mcp.sap.AdtRestClient;

public class AbapCreationCreateObjectTool extends CreateObjectTool {
    public AbapCreationCreateObjectTool(AdtRestClient client) { super(client); }
    @Override public String getName() { return "abap_creation-create_object"; }
}
