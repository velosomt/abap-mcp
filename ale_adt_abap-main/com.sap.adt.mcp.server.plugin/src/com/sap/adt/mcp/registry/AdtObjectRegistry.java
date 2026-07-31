package com.sap.adt.mcp.registry;

import java.util.HashMap;
import java.util.Map;
import java.util.Collection;

import com.sap.adt.mcp.model.AdtObjectDefinition;
import com.sap.adt.mcp.model.AdtObjectDefinition.ParentKind;

/**
 * Singleton registry for ADT Object Definitions.
 */
public class AdtObjectRegistry {
    private static AdtObjectRegistry instance;
    private final Map<String, AdtObjectDefinition> definitions = new HashMap<>();

    private AdtObjectRegistry() {
        registerDefaultDefinitions();
    }

    public static synchronized AdtObjectRegistry getInstance() {
        if (instance == null) {
            instance = new AdtObjectRegistry();
        }
        return instance;
    }

    private void registerDefaultDefinitions() {
        // Classic Objects
        register(new AdtObjectDefinition(
            "Program", "PROG/P", "/sap/bc/adt/programs/programs",
            "application/vnd.sap.adt.programs.programs.v2+xml",
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?><program:abapProgram xmlns:program=\"http://www.sap.com/adt/programs/programs\" xmlns:adtcore=\"http://www.sap.com/adt/core\" adtcore:description=\"${description}\" adtcore:language=\"${language}\" adtcore:name=\"${name}\" adtcore:type=\"PROG/P\" adtcore:masterLanguage=\"${language}\"><adtcore:packageRef adtcore:name=\"${packageName}\"/></program:abapProgram>"
        ));

        register(new AdtObjectDefinition(
            "Class", "CLAS/OC", "/sap/bc/adt/oo/classes",
            "application/vnd.sap.adt.oo.classes.v4+xml",
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?><class:abapClass xmlns:class=\"http://www.sap.com/adt/oo/classes\" xmlns:adtcore=\"http://www.sap.com/adt/core\" adtcore:description=\"${description}\" adtcore:language=\"${language}\" adtcore:name=\"${name}\" adtcore:type=\"CLAS/OC\" adtcore:masterLanguage=\"${language}\" class:final=\"true\" class:visibility=\"public\"><adtcore:packageRef adtcore:name=\"${packageName}\"/></class:abapClass>"
        ));

        register(new AdtObjectDefinition(
            "Interface", "INTF/OI", "/sap/bc/adt/oo/interfaces",
            "application/vnd.sap.adt.oo.interfaces.v5+xml",
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?><intf:abapInterface xmlns:intf=\"http://www.sap.com/adt/oo/interfaces\" xmlns:adtcore=\"http://www.sap.com/adt/core\" adtcore:description=\"${description}\" adtcore:language=\"${language}\" adtcore:name=\"${name}\" adtcore:type=\"INTF/OI\" adtcore:masterLanguage=\"${language}\"><adtcore:packageRef adtcore:name=\"${packageName}\"/></intf:abapInterface>"
        ));

        register(new AdtObjectDefinition(
            "Function Group", "FUGR/F", "/sap/bc/adt/functions/groups",
            "application/*",
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?><group:abapFunctionGroup xmlns:group=\"http://www.sap.com/adt/functions/groups\" xmlns:adtcore=\"http://www.sap.com/adt/core\" adtcore:description=\"${description}\" adtcore:language=\"${language}\" adtcore:name=\"${name}\" adtcore:type=\"FUGR/F\" adtcore:masterLanguage=\"${language}\" adtcore:responsible=\"${responsible}\"><adtcore:packageRef adtcore:name=\"${packageName}\"/></group:abapFunctionGroup>",
            ParentKind.PACKAGE, false
        ));

        register(new AdtObjectDefinition(
            "Include", "PROG/I", "/sap/bc/adt/programs/includes",
            "application/*",
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?><include:abapInclude xmlns:include=\"http://www.sap.com/adt/programs/includes\" xmlns:adtcore=\"http://www.sap.com/adt/core\" adtcore:description=\"${description}\" adtcore:language=\"${language}\" adtcore:name=\"${name}\" adtcore:type=\"PROG/I\" adtcore:masterLanguage=\"${language}\"><adtcore:packageRef adtcore:name=\"${packageName}\"/></include:abapInclude>"
        ));

        register(new AdtObjectDefinition(
            "Function Module", "FUGR/FF", "/sap/bc/adt/functions/groups/%s/fmodules",
            "application/*",
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?><fmodule:abapFunctionModule xmlns:fmodule=\"http://www.sap.com/adt/functions/fmodules\" xmlns:adtcore=\"http://www.sap.com/adt/core\" adtcore:description=\"${description}\" adtcore:name=\"${name}\" adtcore:type=\"FUGR/FF\" adtcore:masterLanguage=\"${language}\"><adtcore:containerRef adtcore:name=\"${packageName}\" adtcore:type=\"FUGR/F\" adtcore:uri=\"${parentUrl}\"/></fmodule:abapFunctionModule>",
            ParentKind.FUNCTION_GROUP, true
        ));

        register(new AdtObjectDefinition(
            "Function Group Include", "FUGR/I", "/sap/bc/adt/functions/groups/%s/includes",
            "application/*",
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?><finclude:abapFunctionGroupInclude xmlns:finclude=\"http://www.sap.com/adt/functions/fincludes\" xmlns:adtcore=\"http://www.sap.com/adt/core\" adtcore:description=\"${description}\" adtcore:name=\"${name}\" adtcore:type=\"FUGR/I\" adtcore:masterLanguage=\"${language}\"><adtcore:containerRef adtcore:name=\"${packageName}\" adtcore:type=\"FUGR/F\" adtcore:uri=\"${parentUrl}\"/></finclude:abapFunctionGroupInclude>",
            ParentKind.FUNCTION_GROUP, true
        ));

        // RAP Objects (SRVD and BDEF via REST, DDLS/DDLX/DCLS/DDLA below have DDL-source content)
        // SRVD is a DDL-source object: shell created here, "define service {...}" written via /source/main
        // Correct endpoint per abap-adt-api (objectcreator.ts): /sap/bc/adt/ddic/srvd/sources + srvdSourceType="S".
        // The old /businessservices/definitions path returns HTTP 404 on S/4 (confirmed vhssvds4ci 2026-06-23).
        register(new AdtObjectDefinition(
            "Service Definition", "SRVD/SRV", "/sap/bc/adt/ddic/srvd/sources",
            "application/*",
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?><srvd:srvdSource xmlns:srvd=\"http://www.sap.com/adt/ddic/srvdsources\" xmlns:adtcore=\"http://www.sap.com/adt/core\" adtcore:description=\"${description}\" adtcore:language=\"${language}\" adtcore:name=\"${name}\" adtcore:type=\"SRVD/SRV\" adtcore:masterLanguage=\"${language}\" srvd:srvdSourceType=\"S\"><adtcore:packageRef adtcore:name=\"${packageName}\"/></srvd:srvdSource>"
        ));

        register(new AdtObjectDefinition(
            "Service Binding", "SRVB/SRV", "/sap/bc/adt/businessservices/bindings",
            "application/vnd.sap.adt.businessservices.servicebinding.v2+xml",
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?><srvb:serviceBinding xmlns:srvb=\"http://www.sap.com/adt/ddic/ServiceBindings\" xmlns:adtcore=\"http://www.sap.com/adt/core\" adtcore:description=\"${description}\" adtcore:name=\"${name}\" adtcore:type=\"SRVB/SVB\" adtcore:responsible=\"${responsible}\"><adtcore:packageRef adtcore:name=\"${packageName}\"/><srvb:services srvb:name=\"${name}\"><srvb:content srvb:version=\"0001\"><srvb:serviceDefinition adtcore:name=\"${serviceDefinition}\"/></srvb:content></srvb:services><srvb:binding srvb:category=\"${bindingCategory}\" srvb:type=\"${bindingType}\" srvb:version=\"${bindingVersion}\"><srvb:implementation adtcore:name=\"\"/></srvb:binding></srvb:serviceBinding>",
            ParentKind.PACKAGE, false
        ));

        // BDEF is a source object: shell here, "managed implementation in class ... { ... }" / "projection;" ...
        // written afterwards via /source/main. The implementation type (managed|unmanaged|projection|abstract|
        // interface) is declared on the FIRST LINE OF THE SOURCE, NOT on the shell (confirmed by reading an
        // active BDEF: A_DIGITALVEHICLE source starts with "projection;\nstrict;").
        // Correct endpoint is /sap/bc/adt/bo/behaviordefinitions (the old /bopf/bdef/sources returns HTTP 404 on S/4).
        //
        // The creation shell MUST use the generic workbench-object envelope
        // <blue:blueSource xmlns:blue="http://www.sap.com/wbobj/blue" ... adtcore:type="BDEF/BO" ...> — the SAME
        // root element TABL/DT and TABL/DS use. The behaviordefinitions handler is a "blue" wbobj handler:
        // posting the bespoke <bdef:behaviorDefinition> root was rejected with
        //   HTTP 400 ExceptionInvalidData "System expected the element '{http://www.sap.com/wbobj/blue}blueSource'"
        // (confirmed against vhssvds4ci 2026-06-23).
        //
        // Content-Type MUST be the generic "application/*" (same as every other source object below:
        // DDLS/DCLS/DDLX/DDLA/SRVD/TABL...). The versioned media type
        // "application/vnd.sap.adt.bo.behaviordefinitions.v1+xml" is the read/write source media type, NOT a
        // creation media type, so the shell POST was first rejected with HTTP 415 Unsupported Media Type.
        // abap-adt-api (objectcreator.ts) also creates every object with the generic "application/*".
        register(new AdtObjectDefinition(
            "Behavior Definition", "BDEF/BO", "/sap/bc/adt/bo/behaviordefinitions",
            "application/*",
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?><blue:blueSource xmlns:blue=\"http://www.sap.com/wbobj/blue\" xmlns:adtcore=\"http://www.sap.com/adt/core\" adtcore:description=\"${description}\" adtcore:language=\"${language}\" adtcore:name=\"${name}\" adtcore:type=\"BDEF/BO\" adtcore:masterLanguage=\"${language}\"><adtcore:packageRef adtcore:name=\"${packageName}\"/></blue:blueSource>"
        ));

        register(new AdtObjectDefinition(
            "Data Definition (CDS View)", "DDLS/DF", "/sap/bc/adt/ddic/ddl/sources",
            "application/*",
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?><ddl:ddlSource xmlns:ddl=\"http://www.sap.com/adt/ddic/ddlsources\" xmlns:adtcore=\"http://www.sap.com/adt/core\" adtcore:description=\"${description}\" adtcore:language=\"${language}\" adtcore:name=\"${name}\" adtcore:type=\"DDLS/DF\" adtcore:masterLanguage=\"${language}\"><adtcore:packageRef adtcore:name=\"${packageName}\"/></ddl:ddlSource>"
        ));

        register(new AdtObjectDefinition(
            "Access Control (DCL)", "DCLS/DL", "/sap/bc/adt/acm/dcl/sources",
            "application/*",
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?><dcl:dclSource xmlns:dcl=\"http://www.sap.com/adt/acm/dclsources\" xmlns:adtcore=\"http://www.sap.com/adt/core\" adtcore:description=\"${description}\" adtcore:language=\"${language}\" adtcore:name=\"${name}\" adtcore:type=\"DCLS/DL\" adtcore:masterLanguage=\"${language}\"><adtcore:packageRef adtcore:name=\"${packageName}\"/></dcl:dclSource>"
        ));

        register(new AdtObjectDefinition(
            "Metadata Extension", "DDLX/EX", "/sap/bc/adt/ddic/ddlx/sources",
            "application/*",
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?><ddlx:ddlxSource xmlns:ddlx=\"http://www.sap.com/adt/ddic/ddlxsources\" xmlns:adtcore=\"http://www.sap.com/adt/core\" adtcore:description=\"${description}\" adtcore:language=\"${language}\" adtcore:name=\"${name}\" adtcore:type=\"DDLX/EX\" adtcore:masterLanguage=\"${language}\"><adtcore:packageRef adtcore:name=\"${packageName}\"/></ddlx:ddlxSource>"
        ));

        register(new AdtObjectDefinition(
            "Annotation Definition", "DDLA/ADF", "/sap/bc/adt/ddic/ddla/sources",
            "application/*",
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?><ddla:ddlaSource xmlns:ddla=\"http://www.sap.com/adt/ddic/ddlasources\" xmlns:adtcore=\"http://www.sap.com/adt/core\" adtcore:description=\"${description}\" adtcore:language=\"${language}\" adtcore:name=\"${name}\" adtcore:type=\"DDLA/ADF\" adtcore:masterLanguage=\"${language}\"><adtcore:packageRef adtcore:name=\"${packageName}\"/></ddla:ddlaSource>"
        ));

        register(new AdtObjectDefinition(
            "Table", "TABL/DT", "/sap/bc/adt/ddic/tables",
            "application/*",
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?><blue:blueSource xmlns:blue=\"http://www.sap.com/wbobj/blue\" xmlns:adtcore=\"http://www.sap.com/adt/core\" adtcore:description=\"${description}\" adtcore:language=\"${language}\" adtcore:name=\"${name}\" adtcore:type=\"TABL/DT\" adtcore:masterLanguage=\"${language}\"><adtcore:packageRef adtcore:name=\"${packageName}\"/></blue:blueSource>"
        ));

        register(new AdtObjectDefinition(
            "Structure", "TABL/DS", "/sap/bc/adt/ddic/structures",
            "application/*",
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?><blue:blueSource xmlns:blue=\"http://www.sap.com/wbobj/blue\" xmlns:adtcore=\"http://www.sap.com/adt/core\" adtcore:description=\"${description}\" adtcore:language=\"${language}\" adtcore:name=\"${name}\" adtcore:type=\"TABL/DS\" adtcore:masterLanguage=\"${language}\"><adtcore:packageRef adtcore:name=\"${packageName}\"/></blue:blueSource>"
        ));

        register(new AdtObjectDefinition(
            "Data Element", "DTEL/DE", "/sap/bc/adt/ddic/dataelements",
            "application/*",
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?><blue:wbobj xmlns:blue=\"http://www.sap.com/wbobj/dictionary/dtel\" xmlns:adtcore=\"http://www.sap.com/adt/core\" adtcore:description=\"${description}\" adtcore:language=\"${language}\" adtcore:name=\"${name}\" adtcore:type=\"DTEL/DE\" adtcore:masterLanguage=\"${language}\"><adtcore:packageRef adtcore:name=\"${packageName}\"/></blue:wbobj>",
            ParentKind.PACKAGE, false
        ));

        register(new AdtObjectDefinition(
            "Domain", "DOMA/DD", "/sap/bc/adt/ddic/domains",
            "application/*",
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?><domain:domain xmlns:domain=\"http://www.sap.com/dictionary/domain\" xmlns:adtcore=\"http://www.sap.com/adt/core\" adtcore:description=\"${description}\" adtcore:language=\"${language}\" adtcore:name=\"${name}\" adtcore:type=\"DOMA/DD\" adtcore:masterLanguage=\"${language}\"><adtcore:packageRef adtcore:name=\"${packageName}\"/></domain:domain>",
            ParentKind.PACKAGE, false
        ));

        register(new AdtObjectDefinition(
            "Message Class", "MSAG/N", "/sap/bc/adt/messageclass",
            "application/*",
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?><mc:messageClass xmlns:mc=\"http://www.sap.com/adt/MessageClass\" xmlns:adtcore=\"http://www.sap.com/adt/core\" adtcore:description=\"${description}\" adtcore:language=\"${language}\" adtcore:name=\"${name}\" adtcore:type=\"MSAG/N\" adtcore:masterLanguage=\"${language}\"><adtcore:packageRef adtcore:name=\"${packageName}\"/></mc:messageClass>",
            ParentKind.PACKAGE, false
        ));

        // Package. Confirmed shape per abap-adt-api (objectcreator.ts createBodyPackage) 2026-07-01 --
        // NOT yet tested against vhssvds4ci (no build pipeline available at write time). softwareComponent is
        // hardcoded to LOCAL (matches $TMP-style local packages); pass a real dev package as parentName
        // (=pak:superPackage) for anything meant to be transportable.
        register(new AdtObjectDefinition(
            "Package", "DEVC/K", "/sap/bc/adt/packages",
            "application/*",
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?><pak:package xmlns:pak=\"http://www.sap.com/adt/packages\" xmlns:adtcore=\"http://www.sap.com/adt/core\" adtcore:description=\"${description}\" adtcore:name=\"${name}\" adtcore:type=\"DEVC/K\" adtcore:responsible=\"${responsible}\"><pak:attributes pak:packageType=\"development\"/><pak:superPackage adtcore:name=\"${packageName}\"/><pak:applicationComponent/><pak:transport><pak:softwareComponent pak:name=\"LOCAL\"/><pak:transportLayer/></pak:transport><pak:translation/><pak:useAccesses/><pak:packageInterfaces/><pak:subPackages/></pak:package>",
            ParentKind.PACKAGE, false
        ));

        // Authorization Field. Confirmed shape per abap-adt-api (objectcreator.ts, generic createBodySimple)
        // 2026-07-01 -- NOT yet tested against vhssvds4ci (no build pipeline available at write time).
        register(new AdtObjectDefinition(
            "Authorization Field", "AUTH", "/sap/bc/adt/aps/iam/auth",
            "application/*",
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?><auth:auth xmlns:auth=\"http://www.sap.com/iam/auth\" xmlns:adtcore=\"http://www.sap.com/adt/core\" adtcore:description=\"${description}\" adtcore:language=\"${language}\" adtcore:name=\"${name}\" adtcore:type=\"AUTH\" adtcore:masterLanguage=\"${language}\" adtcore:responsible=\"${responsible}\"><adtcore:packageRef adtcore:name=\"${packageName}\"/></auth:auth>",
            ParentKind.PACKAGE, false
        ));

        // Authorization Object. Confirmed shape per abap-adt-api (objectcreator.ts, generic createBodySimple)
        // 2026-07-01 -- NOT yet tested against vhssvds4ci (no build pipeline available at write time).
        register(new AdtObjectDefinition(
            "Authorization Object", "SUSO/B", "/sap/bc/adt/aps/iam/suso",
            "application/*",
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?><susob:suso xmlns:susob=\"http://www.sap.com/iam/suso\" xmlns:adtcore=\"http://www.sap.com/adt/core\" adtcore:description=\"${description}\" adtcore:language=\"${language}\" adtcore:name=\"${name}\" adtcore:type=\"SUSO/B\" adtcore:masterLanguage=\"${language}\" adtcore:responsible=\"${responsible}\"><adtcore:packageRef adtcore:name=\"${packageName}\"/></susob:suso>",
            ParentKind.PACKAGE, false
        ));

        // TTYP (Table Type), ENQU (Lock Object), SHLP (Search Help), SFPF/SFPI (Adobe Form/Interface),
        // ENHO/ENHS (Enhancement Implementation/Spot): deliberately NOT registered yet. No confirmed
        // creation URL/XML shape was found (neither in abap-adt-api nor via web research 2026-07-01) --
        // guessing here risks the same 400/404/415 churn documented above for BDEF/SRVD, but without a live
        // system to iterate against right now. Needs either (a) empirical trial once the plugin can be
        // rebuilt+redeployed, or (b) a captured Eclipse ADT network trace of a real "New > ..." wizard run
        // for one of these types, as ground truth.
    }

    public void register(AdtObjectDefinition definition) {
        definitions.put(definition.getAdtType().toUpperCase(), definition);
    }

    public AdtObjectDefinition getDefinition(String adtType) {
        return definitions.get(AdtTypeAlias.normalize(adtType));
    }

    public Collection<String> getSupportedTypes() {
        return definitions.keySet();
    }

    public Collection<AdtObjectDefinition> getAllDefinitions() {
        return definitions.values();
    }
}
