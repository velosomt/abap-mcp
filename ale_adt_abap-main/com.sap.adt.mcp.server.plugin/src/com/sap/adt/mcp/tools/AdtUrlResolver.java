package com.sap.adt.mcp.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sap.adt.mcp.model.AdtObjectDefinition;
import com.sap.adt.mcp.model.AdtObjectDefinition.ParentKind;
import com.sap.adt.mcp.registry.AdtObjectRegistry;

/**
 * Utility for resolving ABAP object types and names to ADT REST URLs.
 * Derives from {@link AdtObjectRegistry} instead of keeping a separate URL map,
 * so every type registered there is automatically resolvable here too.
 */
public final class AdtUrlResolver {

    private AdtUrlResolver() {}

    public static String resolveObjectUrl(String objectType, String objectName) {
        if (objectType == null || objectType.isEmpty()
                || objectName == null || objectName.isEmpty()) {
            return null;
        }
        AdtObjectDefinition def = AdtObjectRegistry.getInstance().getDefinition(objectType);
        if (def == null || def.getParentKind() != ParentKind.PACKAGE) {
            return null;
        }
        return def.getCreationUrl() + "/" + objectName.toLowerCase();
    }

    public static String resolveSourceUrl(String objectType, String objectName) {
        String objectUrl = resolveObjectUrl(objectType, objectName);
        if (objectUrl != null) {
            return objectUrl + "/source/main";
        }
        return null;
    }

    public static JsonArray buildTypeEnumArray() {
        JsonArray arr = new JsonArray();
        for (AdtObjectDefinition def : AdtObjectRegistry.getInstance().getAllDefinitions()) {
            if (def.getParentKind() == ParentKind.PACKAGE) {
                arr.add(def.getAdtType());
            }
        }
        return arr;
    }

    public static final String TYPE_DESCRIPTION =
            "ADT object type (TADIR-style key), e.g. PROG/P (program), PROG/I (include), CLAS/OC (class), "
            + "INTF/OI (interface), DDLS/DF (CDS view), DCLS/DL (access control/DCL), DDLX/EX (metadata extension), "
            + "DDLA/ADF (annotation definition), TABL/DT (table), TABL/DS (structure), DTEL/DE (data element), "
            + "DOMA/DD (domain), MSAG/N (message class), SRVD/SRV (service definition), SRVB/SRV (service binding), "
            + "BDEF/BO (behavior definition). Short aliases (CDS, TABLE, DOMAIN, FUNC, etc) are also accepted. "
            + "Function modules/includes (FUGR/FF, FUGR/I) live inside a function group and are not resolvable here "
            + "by name alone -- use objectSourceUrl for those.";

    public static JsonObject buildTypeProperty() {
        JsonObject prop = new JsonObject();
        prop.addProperty("type", "string");
        prop.addProperty("description", TYPE_DESCRIPTION);
        prop.add("enum", buildTypeEnumArray());
        return prop;
    }

    public static JsonObject buildNameProperty() {
        JsonObject prop = new JsonObject();
        prop.addProperty("type", "string");
        prop.addProperty("description", "Object name (e.g. 'ZCL_MY_CLASS', 'MARA'). Case-insensitive.");
        return prop;
    }
}
