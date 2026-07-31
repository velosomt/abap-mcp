package com.sap.adt.mcp.registry;

import java.util.HashMap;
import java.util.Map;

/**
 * Normalizes short/common ABAP object type codes (e.g. "CDS", "TABLE", "FUNC")
 * into the canonical TADIR-style key used by {@link AdtObjectRegistry} (e.g. "DDLS/DF").
 */
public final class AdtTypeAlias {

    private AdtTypeAlias() {}

    private static final Map<String, String> ALIASES = new HashMap<>();
    static {
        put("PROG", "PROG/P");
        put("PROGRAM", "PROG/P");
        put("REPORT", "PROG/P");
        put("INCLUDE", "PROG/I");
        put("PROG/I", "PROG/I");

        put("CLAS", "CLAS/OC");
        put("CLASS", "CLAS/OC");

        put("INTF", "INTF/OI");
        put("INTERFACE", "INTF/OI");

        put("FUGR", "FUGR/F");
        put("FUNCTIONGROUP", "FUGR/F");
        put("FUNC", "FUGR/FF");
        put("FUNCTIONMODULE", "FUGR/FF");
        put("FM", "FUGR/FF");
        put("FUGR/I", "FUGR/I");
        put("FUNCTIONGROUPINCLUDE", "FUGR/I");

        put("DDLS", "DDLS/DF");
        put("CDS", "DDLS/DF");
        put("DDL", "DDLS/DF");

        put("DCLS", "DCLS/DL");
        put("DCL", "DCLS/DL");
        put("ACCESSCONTROL", "DCLS/DL");

        put("DDLX", "DDLX/EX");
        put("METADATAEXTENSION", "DDLX/EX");

        put("DDLA", "DDLA/ADF");
        put("ANNOTATIONDEFINITION", "DDLA/ADF");

        put("TABL", "TABL/DT");
        put("TABLE", "TABL/DT");
        put("STRU", "TABL/DS");
        put("STRUCTURE", "TABL/DS");

        put("DTEL", "DTEL/DE");
        put("DATAELEMENT", "DTEL/DE");

        put("DOMA", "DOMA/DD");
        put("DOMAIN", "DOMA/DD");

        put("MSAG", "MSAG/N");
        put("MESSAGECLASS", "MSAG/N");

        put("SRVD", "SRVD/SRV");
        put("SERVICEDEFINITION", "SRVD/SRV");

        put("SRVB", "SRVB/SRV");
        put("SERVICEBINDING", "SRVB/SRV");

        put("BDEF", "BDEF/BO");
        put("BEHAVIORDEFINITION", "BDEF/BO");
    }

    private static void put(String alias, String canonical) {
        ALIASES.put(alias.toUpperCase(), canonical);
    }

    /**
     * Resolves a short or already-canonical type code to its canonical TADIR-style key.
     * Returns the (uppercased) input unchanged if no alias is known, so an already
     * correct or unrecognized type still reaches the registry lookup as-is.
     */
    public static String normalize(String type) {
        if (type == null) return null;
        String upper = type.trim().toUpperCase();
        return ALIASES.getOrDefault(upper, upper);
    }
}
