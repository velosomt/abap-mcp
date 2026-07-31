package com.sap.adt.mcp.model;

/**
 * POJO for ADT Object Definitions.
 */
public class AdtObjectDefinition {

    /**
     * Whether {@code parentName} passed to sap_create_object refers to an ABAP package
     * (the common case) or to a containing function group (function modules / includes).
     */
    public enum ParentKind {
        PACKAGE,
        FUNCTION_GROUP
    }

    private String name;
    private String adtType;
    private String creationUrl;
    private String contentType;
    private String templateContent;
    private ParentKind parentKind;
    private boolean supportsSource;

    public AdtObjectDefinition(String name, String adtType, String creationUrl, String contentType, String templateContent) {
        this(name, adtType, creationUrl, contentType, templateContent, ParentKind.PACKAGE, true);
    }

    public AdtObjectDefinition(String name, String adtType, String creationUrl, String contentType, String templateContent,
            ParentKind parentKind, boolean supportsSource) {
        this.name = name;
        this.adtType = adtType;
        this.creationUrl = creationUrl;
        this.contentType = contentType;
        this.templateContent = templateContent;
        this.parentKind = parentKind;
        this.supportsSource = supportsSource;
    }

    public String getName() {
        return name;
    }

    public String getAdtType() {
        return adtType;
    }

    public String getCreationUrl() {
        return creationUrl;
    }

    public String getContentType() {
        return contentType;
    }

    public String getTemplateContent() {
        return templateContent;
    }

    public ParentKind getParentKind() {
        return parentKind;
    }

    public boolean supportsSource() {
        return supportsSource;
    }
}
