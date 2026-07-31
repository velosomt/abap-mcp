package com.sap.adt.mcp.tools;

import java.io.IOException;
import java.net.http.HttpResponse;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sap.adt.mcp.model.AdtObjectDefinition;
import com.sap.adt.mcp.model.AdtObjectDefinition.ParentKind;
import com.sap.adt.mcp.registry.AdtObjectRegistry;
import com.sap.adt.mcp.registry.AdtTypeAlias;
import com.sap.adt.mcp.sap.AdtRestClient;

/**
 * Tool: sap_create_object -- Create new ABAP objects (classic and RAP) from registry templates.
 */
public class CreateObjectTool extends AbstractMcpTool {

    public static final String NAME = "sap_create_object";

    public CreateObjectTool(AdtRestClient client) {
        super(client);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return "Cria objetos ABAP no SAP (PROG, CLAS, INTF, FUGR, DDLS/CDS, DCLS, DDLX, DDLA, TABL, STRU, DTEL, DOMA, "
                + "MSAG, SRVD, SRVB, BDEF, etc). Use 'initialSource' para gravar a fonte inicial automaticamente "
                + "nos tipos que suportam fonte textual.";
    }

    @Override
    public JsonObject getInputSchema() {
        JsonObject objtypeProp = new JsonObject();
        objtypeProp.addProperty("type", "string");
        objtypeProp.addProperty("description",
                "ADT object type: " + AdtObjectRegistry.getInstance().getSupportedTypes().toString()
                + ". Short aliases (CDS, TABLE, DOMAIN, FUNC...) are also accepted.");

        JsonObject nameProp = new JsonObject();
        nameProp.addProperty("type", "string");
        nameProp.addProperty("description", "Object name (e.g. 'ZTEST_PROGRAM')");

        JsonObject parentNameProp = new JsonObject();
        parentNameProp.addProperty("type", "string");
        parentNameProp.addProperty("description",
                "Parent ABAP package name (e.g. '$TMP'). For FUGR/FF (function module) and FUGR/I "
                + "(function group include), this is instead the name of the containing function group.");

        JsonObject descProp = new JsonObject();
        descProp.addProperty("type", "string");
        descProp.addProperty("description", "Short description");

        JsonObject transportProp = new JsonObject();
        transportProp.addProperty("type", "string");
        transportProp.addProperty("description", "Optional transport request number");

        JsonObject initialSourceProp = new JsonObject();
        initialSourceProp.addProperty("type", "string");
        initialSourceProp.addProperty("description",
                "Optional initial source code, written via lock->write->activate->unlock right after the shell "
                + "is created. Only applied for types with textual source support; ignored (with a warning in the "
                + "response) for form-only types like DOMA/DD, DTEL/DE, MSAG/N, SRVB/SRV, FUGR/F.");

        JsonObject serviceDefinitionProp = new JsonObject();
        serviceDefinitionProp.addProperty("type", "string");
        serviceDefinitionProp.addProperty("description",
                "Service Definition name this binding exposes. Required when objtype is SRVB/SRV; ignored for all other types.");

        JsonObject bindingTypeProp = new JsonObject();
        bindingTypeProp.addProperty("type", "string");
        bindingTypeProp.addProperty("description",
                "Service Binding protocol (currently SAP only supports 'ODATA'). Only used for SRVB/SRV. Default: 'ODATA'.");

        JsonObject bindingCategoryProp = new JsonObject();
        bindingCategoryProp.addProperty("type", "string");
        bindingCategoryProp.addProperty("description",
                "Service Binding category: '0' for UI service, '1' for Web API service. Only used for SRVB/SRV. Default: '0'.");

        JsonObject bindingVersionProp = new JsonObject();
        bindingVersionProp.addProperty("type", "string");
        bindingVersionProp.addProperty("description",
                "Service Binding protocol version (e.g. 'V2', 'V4'). Only used for SRVB/SRV. Default: 'V2'.");

        JsonObject properties = new JsonObject();
        properties.add("objtype", objtypeProp);
        properties.add("name", nameProp);
        properties.add("parentName", parentNameProp);
        properties.add("description", descProp);
        properties.add("transport", transportProp);
        properties.add("initialSource", initialSourceProp);
        properties.add("serviceDefinition", serviceDefinitionProp);
        properties.add("bindingType", bindingTypeProp);
        properties.add("bindingCategory", bindingCategoryProp);
        properties.add("bindingVersion", bindingVersionProp);

        JsonArray required = new JsonArray();
        required.add("objtype");
        required.add("name");
        required.add("parentName");
        required.add("description");

        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.add("properties", properties);
        schema.add("required", required);

        return schema;
    }

    @Override
    public String execute(JsonObject arguments) throws Exception {
        String objtype = AdtTypeAlias.normalize(arguments.get("objtype").getAsString());
        String name = arguments.get("name").getAsString().toUpperCase(); // Normalização baseada no abapGit
        String parentName = arguments.get("parentName").getAsString().toUpperCase(); // Pacotes/grupos sempre em maiúsculas
        String description = arguments.get("description").getAsString();
        String transport = optString(arguments, "transport");
        String initialSource = optString(arguments, "initialSource");
        String serviceDefinition = optString(arguments, "serviceDefinition");
        String bindingType = optString(arguments, "bindingType");
        String bindingCategory = optString(arguments, "bindingCategory");
        String bindingVersion = optString(arguments, "bindingVersion");

        if (description.length() > 60) description = description.substring(0, 60);

        AdtObjectDefinition def = AdtObjectRegistry.getInstance().getDefinition(objtype);
        if (def == null) {
            throw new IllegalArgumentException("Unsupported type: " + objtype + ". Supported: " + AdtObjectRegistry.getInstance().getSupportedTypes());
        }

        String srvbValidationWarning = null;
        if ("SRVB/SRV".equals(def.getAdtType())) {
            if (serviceDefinition == null || serviceDefinition.isEmpty()) {
                throw new IllegalArgumentException("objtype SRVB/SRV requires 'serviceDefinition' (the Service Definition name this binding exposes).");
            }
            serviceDefinition = serviceDefinition.toUpperCase();
            if (bindingType == null || bindingType.isEmpty()) bindingType = "ODATA";
            if (bindingCategory == null || bindingCategory.isEmpty()) bindingCategory = "0";
            if (bindingVersion == null || bindingVersion.isEmpty()) bindingVersion = "V2";

            // Mirrors the AWS service_binding_handler.py 5-step pipeline (steps 1-2 only --
            // step 3 transport check, step 4 creation and step 5 activation already exist below /
            // are left to the caller). Step 1 is a hard precondition: SAP's own creation error
            // for a missing SRVD is far less clear than failing here. Step 2 is advisory only in
            // AWS too (it logs a warning and continues on failure), so it never blocks creation.
            requireServiceDefinitionExists(serviceDefinition);
            srvbValidationWarning = validateServiceBindingParameters(name, description, parentName, serviceDefinition, bindingVersion);
        }

        String creationUrl = def.getCreationUrl();
        String parentUrl = null;
        if (def.getParentKind() == ParentKind.FUNCTION_GROUP) {
            parentUrl = "/sap/bc/adt/functions/groups/" + parentName.toLowerCase();
            creationUrl = String.format(creationUrl, parentName.toLowerCase());
        }

        String contentType = def.getContentType();

        // The effective transport may be cleared below if creation is downgraded to a LOCAL object
        // ($TMP, no transport) because the customer's transport-strategy exit blocked the package create.
        String effectiveTransport = transport;
        String xmlBody = buildCreationXml(def, name, parentName, description, parentUrl, serviceDefinition, bindingType, bindingCategory, bindingVersion);

        String path = creationUrl;
        if (transport != null && !transport.isEmpty()) {
            path = path + "?corrNr=" + urlEncode(transport);
        }

        String objectUrl = creationUrl + "/" + name.toLowerCase();

        JsonObject output = new JsonObject();
        output.addProperty("name", name);
        output.addProperty("type", objtype);
        output.addProperty("objectUrl", objectUrl);
        if (srvbValidationWarning != null) {
            output.addProperty("validationWarning", srvbValidationWarning);
        }

        try {
            HttpResponse<String> response = client.post(path, xmlBody, contentType, contentType + ", application/xml");
            output.addProperty("status", "created");
            output.addProperty("statusCode", response.statusCode());
        } catch (IOException e) {
            String msg = e.getMessage() == null ? "" : e.getMessage();
            // Some customers install a transport-strategy exit that resolves the change task from the
            // SAP GUI terminal/hostname. A stateless HTTP/ADT connection has no terminal, so creating
            // in a transportable package is rejected -- seen on vhssvds4ci as
            //   HTTP 403 "Hostname not found, SSP cannot determine current req/task."
            // Per the user's policy for this client: when a transportable-package create is blocked this
            // way, transparently retry as a LOCAL object ($TMP, no transport) instead of failing. Only if
            // the local retry ALSO fails do we surface an error.
            boolean canFallbackLocal = def.getParentKind() == ParentKind.PACKAGE
                    && !"$TMP".equalsIgnoreCase(parentName)
                    && isTransportStrategyBlock(msg);
            if (!canFallbackLocal) {
                output.addProperty("status", "error");
                output.addProperty("errorBody", msg);
                return output.toString();
            }
            try {
                String localXml = buildCreationXml(def, name, "$TMP", description, parentUrl,
                        serviceDefinition, bindingType, bindingCategory, bindingVersion);
                HttpResponse<String> localResp = client.post(creationUrl, localXml, contentType,
                        contentType + ", application/xml");
                effectiveTransport = null; // local object: the source write below must not pass a transport
                output.addProperty("status", "created");
                output.addProperty("statusCode", localResp.statusCode());
                output.addProperty("localFallback", "Creation in package '" + parentName
                        + "' was blocked by the system's transport-strategy exit (\"" + firstLine(msg)
                        + "\"); the object was created LOCALLY in $TMP instead (non-transportable). "
                        + "Reassign it to a transportable package from Eclipse/SE80 when a transport is required.");
            } catch (IOException e2) {
                output.addProperty("status", "error");
                output.addProperty("errorBody", "Create in package '" + parentName + "' was blocked ("
                        + firstLine(msg) + ") and the local $TMP fallback also failed: "
                        + (e2.getMessage() == null ? "" : e2.getMessage()));
                return output.toString();
            }
        }

        if (initialSource != null && !initialSource.isEmpty()) {
            if (!def.supportsSource()) {
                output.addProperty("note", "Type " + objtype + " has no textual source (form-based object); "
                        + "initialSource was ignored. Complete its definition in Eclipse ADT.");
            } else {
                String sourceUrl = objectUrl + "/source/main";
                String writeResultJson = AdtSourceWriter.lockWriteUnlock(client, sourceUrl, initialSource, effectiveTransport);
                output.add("sourceWrite", JsonParser.parseString(writeResultJson));
            }
        } else if (def.supportsSource()) {
            output.addProperty("note", "Shell created. Use sap_set_source on " + objectUrl + "/source/main to write the source.");
        }

        return output.toString();
    }

    /**
     * Step 1 of the SRVB creation pipeline (ported from service_binding_handler.py
     * _validate_service_definition): confirm the referenced SRVD actually exists before
     * attempting to create a binding against it. SAP's own creation-time error for a
     * dangling service definition reference is far less actionable than failing here.
     */
    private void requireServiceDefinitionExists(String serviceDefinition) throws Exception {
        // GET the SRVD *source* with Accept application/* (proven to return 200 for an existing
        // service definition). The object-root URL with Accept application/xml answers HTTP 406,
        // which previously misfired as "not found". Only a real 404 means the SRVD is missing;
        // any other status (406/auth/transient) is treated as "exists" and left for SAP to validate
        // at creation time.
        String srvdUrl = "/sap/bc/adt/ddic/srvd/sources/" + serviceDefinition.toLowerCase() + "/source/main";
        try {
            client.get(srvdUrl, "application/*");
        } catch (Exception e) {
            String msg = e.getMessage() == null ? "" : e.getMessage();
            if (msg.contains("404") || msg.toLowerCase().contains("not found")
                    || msg.toLowerCase().contains("resourcenotfound")) {
                throw new IllegalArgumentException(
                        "Service Definition '" + serviceDefinition + "' was not found (GET " + srvdUrl
                        + " failed: " + msg + "). Create it first with objtype SRVD/SRV before "
                        + "creating a SRVB/SRV binding against it.", e);
            }
            // non-404: assume it exists; SAP will reject at creation time if the reference is dangling
        }
    }

    /**
     * Step 2 of the SRVB creation pipeline (ported from service_binding_handler.py
     * _perform_validation): POST /sap/bc/adt/businessservices/bindings/validation with the
     * intended binding parameters. AWS treats this as advisory only -- a failure here logs a
     * warning and creation proceeds anyway -- so this returns a warning string instead of
     * throwing.
     */
    private String validateServiceBindingParameters(String name, String description, String parentName,
            String serviceDefinition, String bindingVersion) {
        String path = "/sap/bc/adt/businessservices/bindings/validation"
                + "?objname=" + urlEncode(name)
                + "&description=" + urlEncode(description)
                + "&serviceBindingVersion=" + urlEncode(bindingVersion)
                + "&serviceDefinition=" + urlEncode(serviceDefinition)
                + "&package=" + urlEncode(parentName);
        try {
            HttpResponse<String> response = client.post(path, "", "application/xml", "application/vnd.sap.as+xml");
            if (response.statusCode() == 200) {
                return null;
            }
            return "Service binding validation returned HTTP " + response.statusCode()
                    + " (creation proceeded anyway -- this mirrors AWS's advisory-only validation step).";
        } catch (Exception e) {
            return "Service binding validation call failed: " + e.getMessage()
                    + " (creation proceeded anyway -- this mirrors AWS's advisory-only validation step).";
        }
    }

    /**
     * Detects the customer transport-strategy exit error that blocks creating objects in a
     * transportable package over a stateless HTTP/ADT connection (no GUI terminal/hostname to resolve
     * the change task from). Seen on vhssvds4ci as
     * "Hostname not found, SSP cannot determine current req/task." (HTTP 403).
     */
    private static boolean isTransportStrategyBlock(String msg) {
        if (msg == null) return false;
        String m = msg.toLowerCase();
        return m.contains("cannot determine current req/task")
                || m.contains("ssp cannot determine")
                || (m.contains("hostname not found") && m.contains("req/task"));
    }

    /** First line of a (possibly multi-line) error message, capped at 200 chars, for compact notes. */
    private static String firstLine(String msg) {
        if (msg == null) return "";
        String trimmed = msg.trim();
        int nl = trimmed.indexOf('\n');
        String line = nl >= 0 ? trimmed.substring(0, nl) : trimmed;
        return line.length() > 200 ? line.substring(0, 200) : line;
    }

    private String buildCreationXml(AdtObjectDefinition def, String name, String parentName, String description, String parentUrl,
            String serviceDefinition, String bindingType, String bindingCategory, String bindingVersion) {
        String template = def.getTemplateContent();
        String language = client.getLanguage();
        String responsible = client.getUsername();
        String result = template
            .replace("${name}", escapeXml(name))
            .replace("${description}", escapeXml(description))
            .replace("${packageName}", escapeXml(parentName))
            .replace("${language}", escapeXml(language))
            .replace("${responsible}", escapeXml(responsible))
            .replace("${serviceDefinition}", escapeXml(serviceDefinition))
            .replace("${bindingType}", escapeXml(bindingType))
            .replace("${bindingCategory}", escapeXml(bindingCategory))
            .replace("${bindingVersion}", escapeXml(bindingVersion));
        if (parentUrl != null) {
            result = result.replace("${parentUrl}", escapeXml(parentUrl));
        }
        return result;
    }
}
