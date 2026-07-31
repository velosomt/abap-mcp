package com.sap.adt.mcp.sap;

import java.io.StringReader;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * Static utility class for parsing XML responses from SAP ADT REST APIs.
 */
public final class AdtXmlParser {

    private static final String NS_ATOM = "http://www.w3.org/2005/Atom";
    private static final String NS_ADT = "http://www.sap.com/adt/api";
    private static final String NS_ADT_CORE = "http://www.sap.com/adt/core";
    private static final String NS_CHKRUN = "http://www.sap.com/adt/checkrun";

    private AdtXmlParser() {}

    public static JsonArray parseSearchResults(String xml) {
        JsonArray results = new JsonArray();
        if (isBlank(xml)) return results;

        try {
            Document doc = parseDocument(xml);
            NodeList refs = doc.getElementsByTagNameNS(NS_ADT_CORE, "objectReference");
            if (refs.getLength() == 0) {
                refs = doc.getElementsByTagNameNS(NS_ADT, "objectReference");
            }
            if (refs.getLength() == 0) {
                refs = doc.getElementsByTagName("adtcore:objectReference");
            }
            if (refs.getLength() == 0) {
                refs = doc.getElementsByTagName("objectReference");
            }

            if (refs.getLength() > 0) {
                for (int i = 0; i < refs.getLength(); i++) {
                    Element ref = (Element) refs.item(i);
                    JsonObject entry = new JsonObject();
                    entry.addProperty("name", attr(ref, "adtcore:name", attr(ref, "name", "")));
                    entry.addProperty("type", attr(ref, "adtcore:type", attr(ref, "type", "")));
                    entry.addProperty("uri", attr(ref, "uri", ""));
                    entry.addProperty("description", attr(ref, "adtcore:description", attr(ref, "description", "")));
                    entry.addProperty("packageName", attr(ref, "adtcore:packageName", attr(ref, "packageName", "")));
                    results.add(entry);
                }
                return results;
            }

            NodeList entries = doc.getElementsByTagNameNS(NS_ATOM, "entry");
            if (entries.getLength() == 0) {
                entries = doc.getElementsByTagName("entry");
            }

            for (int i = 0; i < entries.getLength(); i++) {
                Element entry = (Element) entries.item(i);
                JsonObject obj = new JsonObject();
                obj.addProperty("name", childText(entry, "title", ""));
                obj.addProperty("uri", childAttr(entry, "link", "href", ""));
                obj.addProperty("type", childText(entry, "category", ""));
                obj.addProperty("description", childText(entry, "summary", ""));
                obj.addProperty("packageName", "");
                results.add(obj);
            }
        } catch (Exception e) {
            System.err.println("AdtXmlParser.parseSearchResults failed: " + e.getMessage());
        }

        return results;
    }

    public static String extractLockHandle(String xml) {
        if (isBlank(xml)) return "";

        try {
            Document doc = parseDocument(xml);
            NodeList nodes = doc.getElementsByTagName("LOCK_HANDLE");
            if (nodes.getLength() > 0) {
                String value = nodes.item(0).getTextContent();
                if (value != null) return value.trim();
            }
            nodes = doc.getElementsByTagName("lock_handle");
            if (nodes.getLength() > 0) {
                String value = nodes.item(0).getTextContent();
                if (value != null) return value.trim();
            }
        } catch (Exception e) {
            System.err.println("AdtXmlParser.extractLockHandle failed: " + e.getMessage());
        }
        return "";
    }

    public static JsonArray parseSyntaxCheckResults(String xml) {
        JsonArray results = new JsonArray();
        if (isBlank(xml)) return results;

        try {
            Document doc = parseDocument(xml);
            NodeList messages = doc.getElementsByTagNameNS(NS_CHKRUN, "checkMessage");
            if (messages.getLength() == 0) {
                messages = doc.getElementsByTagName("chkrun:checkMessage");
            }

            if (messages.getLength() > 0) {
                for (int i = 0; i < messages.getLength(); i++) {
                    Element msg = (Element) messages.item(i);
                    JsonObject finding = new JsonObject();

                    String uri = attr(msg, "chkrun:uri", attr(msg, "uri", ""));
                    finding.addProperty("uri", uri);

                    String line = "";
                    String offset = "";
                    int hashIdx = uri.indexOf("#start=");
                    if (hashIdx >= 0) {
                        String fragment = uri.substring(hashIdx + 7);
                        String[] parts = fragment.split(",");
                        if (parts.length >= 1) line = parts[0];
                        if (parts.length >= 2) offset = parts[1];
                    }
                    finding.addProperty("line", line);
                    finding.addProperty("offset", offset);

                    String type = attr(msg, "chkrun:type", attr(msg, "type", ""));
                    String severity;
                    switch (type.toUpperCase()) {
                        case "E": severity = "error"; break;
                        case "W": severity = "warning"; break;
                        case "I": severity = "info"; break;
                        default: severity = type;
                    }
                    finding.addProperty("severity", severity);
                    finding.addProperty("text", attr(msg, "chkrun:shortText", attr(msg, "shortText", "")));
                    results.add(finding);
                }
            }
        } catch (Exception e) {
            System.err.println("AdtXmlParser.parseSyntaxCheckResults failed: " + e.getMessage());
        }

        return results;
    }

    public static JsonObject parseActivationResult(String xml) {
        JsonObject result = new JsonObject();
        result.addProperty("success", false);
        JsonArray messages = new JsonArray();
        result.add("messages", messages);

        if (isBlank(xml)) return result;

        try {
            Document doc = parseDocument(xml);
            Element root = doc.getDocumentElement();
            String severity = attr(root, "severity", attr(root, "chkrun:severity", ""));
            boolean success = true;

            NodeList msgNodes = doc.getElementsByTagName("msg");
            if (msgNodes == null || msgNodes.getLength() == 0) {
                msgNodes = doc.getElementsByTagName("message");
            }

            for (int i = 0; i < msgNodes.getLength(); i++) {
                Element msgEl = (Element) msgNodes.item(i);
                String text = msgEl.getTextContent();
                if (text == null || text.trim().isEmpty()) {
                    text = attr(msgEl, "text", attr(msgEl, "shortText", ""));
                }

                String msgSeverity = attr(msgEl, "severity", attr(msgEl, "type", "")).toLowerCase();

                if (text != null && !text.trim().isEmpty()) {
                    messages.add(text.trim());
                }

                if (msgSeverity.contains("error") || msgSeverity.equals("e")) {
                    success = false;
                }
            }

            if (severity.equalsIgnoreCase("error") || severity.equalsIgnoreCase("E")) {
                success = false;
            }

            if (messages.size() == 0 && !severity.equalsIgnoreCase("error")) {
                success = true;
            }

            result.addProperty("success", success);

        } catch (Exception e) {
            System.err.println("AdtXmlParser.parseActivationResult failed: " + e.getMessage());
            result.addProperty("success", false);
            messages.add("Parse error: " + e.getMessage());
        }

        return result;
    }

    private static Document parseDocument(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", false);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);

        DocumentBuilder builder = factory.newDocumentBuilder();
        InputSource source = new InputSource(new StringReader(xml));
        return builder.parse(source);
    }

    private static String attr(Element el, String attrName, String defaultValue) {
        if (el == null) return defaultValue;
        String value = el.getAttribute(attrName);
        return (value != null && !value.isEmpty()) ? value : defaultValue;
    }

    private static String childText(Element parent, String tagName, String defaultValue) {
        if (parent == null) return defaultValue;
        NodeList children = parent.getElementsByTagName(tagName);
        if (children.getLength() > 0) {
            String text = children.item(0).getTextContent();
            return (text != null && !text.trim().isEmpty()) ? text.trim() : defaultValue;
        }
        return defaultValue;
    }

    private static String childAttr(Element parent, String tagName, String attrName, String defaultValue) {
        if (parent == null) return defaultValue;
        NodeList children = parent.getElementsByTagName(tagName);
        if (children.getLength() > 0) {
            Element child = (Element) children.item(0);
            return attr(child, attrName, defaultValue);
        }
        return defaultValue;
    }

    /**
     * Parse unit test results XML.
     */
    public static JsonObject parseUnitTestResults(String xml) {
        JsonObject result = new JsonObject();
        result.addProperty("success", true);
        JsonArray alerts = new JsonArray();
        result.add("alerts", alerts);

        if (isBlank(xml)) return result;

        try {
            Document doc = parseDocument(xml);

            // Look for alert nodes
            NodeList alertNodes = doc.getElementsByTagName("alert");
            for (int i = 0; i < alertNodes.getLength(); i++) {
                Element alert = (Element) alertNodes.item(i);
                JsonObject alertObj = new JsonObject();
                alertObj.addProperty("kind", attr(alert, "kind", ""));
                alertObj.addProperty("severity", attr(alert, "severity", ""));

                // Get title and details
                NodeList titles = alert.getElementsByTagName("title");
                if (titles.getLength() > 0) {
                    alertObj.addProperty("title", titles.item(0).getTextContent());
                }

                NodeList details = alert.getElementsByTagName("detail");
                if (details.getLength() > 0) {
                    alertObj.addProperty("detail", details.item(0).getTextContent());
                }

                String severity = attr(alert, "severity", "").toLowerCase();
                if (severity.contains("fatal") || severity.contains("critical")) {
                    result.addProperty("success", false);
                }

                alerts.add(alertObj);
            }

            // Look for program nodes with test results
            NodeList programs = doc.getElementsByTagName("program");
            JsonArray programResults = new JsonArray();
            for (int i = 0; i < programs.getLength(); i++) {
                Element prog = (Element) programs.item(i);
                JsonObject progObj = new JsonObject();
                progObj.addProperty("name", attr(prog, "adtcore:name", attr(prog, "name", "")));
                progObj.addProperty("uri", attr(prog, "adtcore:uri", attr(prog, "uri", "")));

                // Count test classes and methods
                NodeList testClasses = prog.getElementsByTagName("testClass");
                JsonArray classes = new JsonArray();
                for (int j = 0; j < testClasses.getLength(); j++) {
                    Element tc = (Element) testClasses.item(j);
                    JsonObject tcObj = new JsonObject();
                    tcObj.addProperty("name", attr(tc, "adtcore:name", attr(tc, "name", "")));

                    NodeList methods = tc.getElementsByTagName("testMethod");
                    JsonArray methodArr = new JsonArray();
                    for (int k = 0; k < methods.getLength(); k++) {
                        Element m = (Element) methods.item(k);
                        JsonObject mObj = new JsonObject();
                        mObj.addProperty("name", attr(m, "adtcore:name", attr(m, "name", "")));
                        mObj.addProperty("executionTime", attr(m, "executionTime", "0"));

                        // Check for alerts in method
                        NodeList mAlerts = m.getElementsByTagName("alert");
                        if (mAlerts.getLength() > 0) {
                            Element mAlert = (Element) mAlerts.item(0);
                            String kind = attr(mAlert, "kind", "");
                            mObj.addProperty("status", kind.isEmpty() ? "passed" : kind);
                            if (kind.equalsIgnoreCase("failedAssertion") || kind.equalsIgnoreCase("error")) {
                                result.addProperty("success", false);
                            }
                        } else {
                            mObj.addProperty("status", "passed");
                        }
                        methodArr.add(mObj);
                    }
                    tcObj.add("methods", methodArr);
                    classes.add(tcObj);
                }
                progObj.add("testClasses", classes);
                programResults.add(progObj);
            }
            result.add("programs", programResults);

        } catch (Exception e) {
            System.err.println("AdtXmlParser.parseUnitTestResults failed: " + e.getMessage());
        }

        return result;
    }

    /**
     * Parse data preview (SQL query) results XML.
     */
    public static JsonObject parseDataPreview(String xml) {
        JsonObject result = new JsonObject();
        JsonArray columns = new JsonArray();
        JsonArray rows = new JsonArray();
        result.add("columns", columns);
        result.add("rows", rows);

        if (isBlank(xml)) return result;

        try {
            Document doc = parseDocument(xml);

            // O ADT Data Preview (/sap/bc/adt/datapreview/freestyle) é ORIENTADO A COLUNAS:
            //   tableData > columns(N) > metadata[@name,@type,...] + dataSet > data(1 por linha)
            // Cada <columns> é UMA coluna; os valores das linhas vêm dentro do seu <dataSet>.
            NodeList colNodes = doc.getElementsByTagName("dataPreview:columns");
            if (colNodes.getLength() == 0) {
                colNodes = doc.getElementsByTagName("columns");
            }

            // Acumula os valores de cada coluna (para depois transpor em linhas).
            JsonArray perColumnValues = new JsonArray();

            for (int c = 0; c < colNodes.getLength(); c++) {
                Element colEl = (Element) colNodes.item(c);

                // Metadados da coluna (nome/tipo/descrição) ficam no filho <metadata>.
                String name = "COL" + c;
                String type = "";
                String description = "";
                NodeList meta = colEl.getElementsByTagName("dataPreview:metadata");
                if (meta.getLength() == 0) {
                    meta = colEl.getElementsByTagName("metadata");
                }
                if (meta.getLength() > 0) {
                    Element m = (Element) meta.item(0);
                    name = attr(m, "dataPreview:name", attr(m, "name", "COL" + c));
                    type = attr(m, "dataPreview:type", attr(m, "type", ""));
                    description = attr(m, "dataPreview:description", attr(m, "description", ""));
                }

                JsonObject colObj = new JsonObject();
                colObj.addProperty("name", name);
                colObj.addProperty("type", type);
                colObj.addProperty("description", description);
                columns.add(colObj);

                // Valores desta coluna (um <data> por linha) dentro de <dataSet>.
                JsonArray values = new JsonArray();
                NodeList dataNodes = colEl.getElementsByTagName("dataPreview:data");
                if (dataNodes.getLength() == 0) {
                    dataNodes = colEl.getElementsByTagName("data");
                }
                for (int d = 0; d < dataNodes.getLength(); d++) {
                    String val = dataNodes.item(d).getTextContent();
                    values.add(val != null ? val : "");
                }
                perColumnValues.add(values);
            }

            // Transpõe colunas -> linhas: row[r] = [col0[r], col1[r], ...].
            int rowCount = 0;
            for (int c = 0; c < perColumnValues.size(); c++) {
                rowCount = Math.max(rowCount, perColumnValues.get(c).getAsJsonArray().size());
            }
            for (int r = 0; r < rowCount; r++) {
                JsonArray rowData = new JsonArray();
                for (int c = 0; c < perColumnValues.size(); c++) {
                    JsonArray colVals = perColumnValues.get(c).getAsJsonArray();
                    rowData.add(r < colVals.size() ? colVals.get(r).getAsString() : "");
                }
                rows.add(rowData);
            }

            result.addProperty("rowCount", rows.size());

        } catch (Exception e) {
            System.err.println("AdtXmlParser.parseDataPreview failed: " + e.getMessage());
        }

        return result;
    }

    /**
     * Parse the status of an ATC background run from /sap/bc/adt/atc/runs/{runId}.
     * SAP places the status on a namespaced attribute (e.g. "runs:status") whose
     * local name varies, so we scan every attribute of every element for one
     * containing "status" rather than hardcoding a single namespace/prefix.
     */
    public static String parseAtcRunStatus(String xml) {
        if (isBlank(xml)) return "running";

        try {
            Document doc = parseDocument(xml);
            NodeList allElements = doc.getElementsByTagName("*");
            for (int i = 0; i < allElements.getLength(); i++) {
                Element el = (Element) allElements.item(i);
                NamedNodeMap attrs = el.getAttributes();
                for (int j = 0; j < attrs.getLength(); j++) {
                    Attr a = (Attr) attrs.item(j);
                    String name = a.getName();
                    if (name != null && name.toLowerCase().contains("status")) {
                        return a.getValue();
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("AdtXmlParser.parseAtcRunStatus failed: " + e.getMessage());
        }

        return "running";
    }

    /**
     * Parse ATC worklist results XML.
     */
    public static JsonObject parseAtcWorklist(String xml) {
        JsonObject result = new JsonObject();
        JsonArray findings = new JsonArray();
        result.add("findings", findings);
        result.addProperty("totalFindings", 0);

        if (isBlank(xml)) return result;

        try {
            Document doc = parseDocument(xml);

            // Parse findings
            NodeList findingNodes = doc.getElementsByTagName("atcfinding");
            if (findingNodes.getLength() == 0) {
                findingNodes = doc.getElementsByTagName("finding");
            }

            for (int i = 0; i < findingNodes.getLength(); i++) {
                Element f = (Element) findingNodes.item(i);
                JsonObject finding = new JsonObject();
                finding.addProperty("checkId", attr(f, "checkId", ""));
                finding.addProperty("checkTitle", attr(f, "checkTitle", ""));
                finding.addProperty("messageId", attr(f, "messageId", ""));
                finding.addProperty("messageTitle", attr(f, "messageTitle", attr(f, "shortText", "")));
                finding.addProperty("priority", attr(f, "priority", ""));
                finding.addProperty("uri", attr(f, "uri", attr(f, "location", "")));
                finding.addProperty("quickfixInfo", attr(f, "quickfixInfo", ""));
                finding.addProperty("checksum", attr(f, "checksum", ""));

                // Extract line number from URI if present
                String uri = attr(f, "uri", attr(f, "location", ""));
                String line = "";
                int hashIdx = uri.indexOf("#start=");
                if (hashIdx >= 0) {
                    String fragment = uri.substring(hashIdx + 7);
                    String[] parts = fragment.split(",");
                    if (parts.length >= 1) line = parts[0];
                }
                finding.addProperty("line", line);

                findings.add(finding);
            }

            result.addProperty("totalFindings", findings.size());

            // Also check for object-level info
            NodeList objects = doc.getElementsByTagName("atcobject");
            if (objects.getLength() == 0) {
                objects = doc.getElementsByTagName("object");
            }
            JsonArray objectsArr = new JsonArray();
            for (int i = 0; i < objects.getLength(); i++) {
                Element obj = (Element) objects.item(i);
                JsonObject objInfo = new JsonObject();
                objInfo.addProperty("name", attr(obj, "adtcore:name", attr(obj, "name", "")));
                objInfo.addProperty("type", attr(obj, "adtcore:type", attr(obj, "type", "")));
                objInfo.addProperty("uri", attr(obj, "adtcore:uri", attr(obj, "uri", "")));
                objectsArr.add(objInfo);
            }
            if (objectsArr.size() > 0) {
                result.add("objects", objectsArr);
            }

        } catch (Exception e) {
            System.err.println("AdtXmlParser.parseAtcWorklist failed: " + e.getMessage());
        }

        return result;
    }

    /**
     * Parse inactive objects list XML.
     */
    public static JsonObject parseInactiveObjects(String xml) {
        JsonObject result = new JsonObject();
        JsonArray objects = new JsonArray();
        result.add("inactiveObjects", objects);

        if (isBlank(xml)) return result;

        try {
            Document doc = parseDocument(xml);

            // Look for entry or inactiveObject elements
            NodeList entries = doc.getElementsByTagName("entry");
            if (entries.getLength() == 0) {
                entries = doc.getElementsByTagName("inactiveObject");
            }
            if (entries.getLength() == 0) {
                entries = doc.getElementsByTagNameNS(NS_ADT_CORE, "objectReference");
            }
            if (entries.getLength() == 0) {
                entries = doc.getElementsByTagName("objectReference");
            }

            for (int i = 0; i < entries.getLength(); i++) {
                Element entry = (Element) entries.item(i);
                JsonObject obj = new JsonObject();
                obj.addProperty("name", attr(entry, "adtcore:name", attr(entry, "name", childText(entry, "title", ""))));
                obj.addProperty("type", attr(entry, "adtcore:type", attr(entry, "type", "")));
                obj.addProperty("uri", attr(entry, "adtcore:uri", attr(entry, "uri", childAttr(entry, "link", "href", ""))));
                obj.addProperty("description", attr(entry, "adtcore:description", attr(entry, "description", childText(entry, "summary", ""))));
                obj.addProperty("user", attr(entry, "adtcore:responsible", attr(entry, "responsible", "")));
                objects.add(obj);
            }

            result.addProperty("count", objects.size());

        } catch (Exception e) {
            System.err.println("AdtXmlParser.parseInactiveObjects failed: " + e.getMessage());
        }

        return result;
    }

    /**
     * Parse object structure metadata XML.
     */
    public static JsonObject parseObjectStructure(String xml) {
        JsonObject result = new JsonObject();

        if (isBlank(xml)) return result;

        try {
            Document doc = parseDocument(xml);
            Element root = doc.getDocumentElement();

            // Extract basic info from root
            result.addProperty("name", attr(root, "adtcore:name", attr(root, "name", "")));
            result.addProperty("type", attr(root, "adtcore:type", attr(root, "type", "")));
            result.addProperty("description", attr(root, "adtcore:description", attr(root, "description", "")));
            result.addProperty("version", attr(root, "adtcore:version", attr(root, "version", "")));
            result.addProperty("createdBy", attr(root, "adtcore:createdBy", ""));
            result.addProperty("changedBy", attr(root, "adtcore:changedBy", ""));
            result.addProperty("masterLanguage", attr(root, "adtcore:masterLanguage", ""));

            // Package reference
            NodeList pkgRefs = doc.getElementsByTagName("packageRef");
            if (pkgRefs.getLength() == 0) {
                pkgRefs = doc.getElementsByTagNameNS(NS_ADT_CORE, "packageRef");
            }
            if (pkgRefs.getLength() > 0) {
                Element pkg = (Element) pkgRefs.item(0);
                result.addProperty("packageName", attr(pkg, "adtcore:name", attr(pkg, "name", "")));
            }

            // For classes - get includes (definitions, implementations, etc.)
            JsonArray includes = new JsonArray();
            NodeList includeNodes = doc.getElementsByTagName("include");
            for (int i = 0; i < includeNodes.getLength(); i++) {
                Element inc = (Element) includeNodes.item(i);
                JsonObject incObj = new JsonObject();
                incObj.addProperty("name", attr(inc, "adtcore:name", attr(inc, "name", "")));
                incObj.addProperty("type", attr(inc, "adtcore:type", attr(inc, "type", "")));
                incObj.addProperty("includeType", attr(inc, "includeType", attr(inc, "class:includeType", "")));
                String uri = attr(inc, "adtcore:uri", attr(inc, "uri", ""));
                incObj.addProperty("uri", uri);

                // Source URI for accessing the code
                NodeList links = inc.getElementsByTagName("link");
                for (int j = 0; j < links.getLength(); j++) {
                    Element link = (Element) links.item(j);
                    String rel = attr(link, "rel", "");
                    if (rel.contains("source") || rel.contains("main")) {
                        incObj.addProperty("sourceUri", attr(link, "href", ""));
                        break;
                    }
                }

                includes.add(incObj);
            }
            if (includes.size() > 0) {
                result.add("includes", includes);
            }

            // For function groups - get function modules
            JsonArray functions = new JsonArray();
            NodeList funcNodes = doc.getElementsByTagName("fmodule");
            if (funcNodes.getLength() == 0) {
                funcNodes = doc.getElementsByTagName("functionModule");
            }
            for (int i = 0; i < funcNodes.getLength(); i++) {
                Element func = (Element) funcNodes.item(i);
                JsonObject funcObj = new JsonObject();
                funcObj.addProperty("name", attr(func, "adtcore:name", attr(func, "name", "")));
                funcObj.addProperty("description", attr(func, "adtcore:description", attr(func, "description", "")));
                funcObj.addProperty("uri", attr(func, "adtcore:uri", attr(func, "uri", "")));
                functions.add(funcObj);
            }
            if (functions.size() > 0) {
                result.add("functionModules", functions);
            }

            // Links for navigation
            JsonArray links = new JsonArray();
            NodeList linkNodes = doc.getElementsByTagName("link");
            if (linkNodes.getLength() == 0) {
                linkNodes = doc.getElementsByTagNameNS(NS_ATOM, "link");
            }
            for (int i = 0; i < linkNodes.getLength(); i++) {
                Element link = (Element) linkNodes.item(i);
                JsonObject linkObj = new JsonObject();
                linkObj.addProperty("rel", attr(link, "rel", ""));
                linkObj.addProperty("href", attr(link, "href", ""));
                linkObj.addProperty("type", attr(link, "type", ""));
                links.add(linkObj);
            }
            if (links.size() > 0) {
                result.add("links", links);
            }

        } catch (Exception e) {
            System.err.println("AdtXmlParser.parseObjectStructure failed: " + e.getMessage());
        }

        return result;
    }

    /**
     * Parse ABAP documentation response (HTML/XML mixed content).
     * Extracts readable text from the documentation.
     */
    public static String parseAbapDocu(String content) {
        if (isBlank(content)) return "";

        StringBuilder result = new StringBuilder();

        try {
            // If it's XML, try to parse it
            if (content.trim().startsWith("<?xml") || content.trim().startsWith("<")) {
                try {
                    Document doc = parseDocument(content);

                    // Look for documentation text in various elements
                    NodeList docuNodes = doc.getElementsByTagName("documentation");
                    if (docuNodes.getLength() > 0) {
                        result.append(extractTextContent(docuNodes.item(0)));
                    }

                    // Also check for docu:documentation
                    docuNodes = doc.getElementsByTagName("docu:documentation");
                    if (docuNodes.getLength() > 0) {
                        result.append(extractTextContent(docuNodes.item(0)));
                    }

                    // Check for shortText elements
                    NodeList shortTexts = doc.getElementsByTagName("shortText");
                    for (int i = 0; i < shortTexts.getLength(); i++) {
                        String text = shortTexts.item(i).getTextContent();
                        if (text != null && !text.trim().isEmpty()) {
                            if (result.length() > 0) result.append("\n");
                            result.append(text.trim());
                        }
                    }

                    // Check for longText elements
                    NodeList longTexts = doc.getElementsByTagName("longText");
                    for (int i = 0; i < longTexts.getLength(); i++) {
                        String text = longTexts.item(i).getTextContent();
                        if (text != null && !text.trim().isEmpty()) {
                            if (result.length() > 0) result.append("\n\n");
                            result.append(text.trim());
                        }
                    }

                    // If still empty, try to get all text content
                    if (result.length() == 0) {
                        result.append(cleanHtmlTags(doc.getDocumentElement().getTextContent()));
                    }
                } catch (Exception e) {
                    // If XML parsing fails, treat as HTML/text
                    result.append(cleanHtmlTags(content));
                }
            } else {
                // Plain text or HTML
                result.append(cleanHtmlTags(content));
            }
        } catch (Exception e) {
            System.err.println("AdtXmlParser.parseAbapDocu failed: " + e.getMessage());
            // Return cleaned content as fallback
            return cleanHtmlTags(content);
        }

        return result.toString().trim();
    }

    /**
     * Extract text content from a node, preserving some structure.
     */
    private static String extractTextContent(Node node) {
        if (node == null) return "";

        StringBuilder sb = new StringBuilder();
        NodeList children = node.getChildNodes();

        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.TEXT_NODE) {
                String text = child.getTextContent();
                if (text != null && !text.trim().isEmpty()) {
                    sb.append(text.trim()).append(" ");
                }
            } else if (child.getNodeType() == Node.ELEMENT_NODE) {
                String tagName = child.getNodeName().toLowerCase();
                // Add newlines for block elements
                if (tagName.equals("p") || tagName.equals("br") || tagName.equals("div")
                        || tagName.equals("li") || tagName.equals("tr")) {
                    sb.append("\n");
                }
                sb.append(extractTextContent(child));
                if (tagName.equals("p") || tagName.equals("div") || tagName.equals("li")) {
                    sb.append("\n");
                }
            }
        }

        return sb.toString();
    }

    /**
     * Remove HTML tags and clean up text.
     */
    private static String cleanHtmlTags(String html) {
        if (html == null) return "";

        // Replace common HTML entities
        String text = html
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&")
                .replace("&nbsp;", " ")
                .replace("&quot;", "\"")
                .replace("&#39;", "'");

        // Replace block elements with newlines
        text = text.replaceAll("(?i)<br\\s*/?>", "\n");
        text = text.replaceAll("(?i)</p>", "\n\n");
        text = text.replaceAll("(?i)</div>", "\n");
        text = text.replaceAll("(?i)</li>", "\n");
        text = text.replaceAll("(?i)</tr>", "\n");
        text = text.replaceAll("(?i)<li>", "• ");

        // Remove all remaining HTML tags
        text = text.replaceAll("<[^>]+>", "");

        // Clean up whitespace
        text = text.replaceAll("[ \\t]+", " ");
        text = text.replaceAll("\n ", "\n");
        text = text.replaceAll(" \n", "\n");
        text = text.replaceAll("\n{3,}", "\n\n");

        return text.trim();
    }

    /**
     * Parse the response of POST /sap/bc/adt/cts/transportchecks: a list of
     * transport requests (TRKORR) the object/package combination is allowed to use.
     */
    public static JsonObject parseTransportCheck(String xml) {
        JsonObject result = new JsonObject();
        JsonArray transports = new JsonArray();
        result.add("transports", transports);

        if (isBlank(xml)) return result;

        try {
            Document doc = parseDocument(xml);
            NodeList allElements = doc.getElementsByTagName("*");
            for (int i = 0; i < allElements.getLength(); i++) {
                Element el = (Element) allElements.item(i);
                String local = el.getLocalName() != null ? el.getLocalName() : el.getTagName();
                if ("TRKORR".equalsIgnoreCase(local)) {
                    String value = el.getTextContent();
                    if (value != null && !value.trim().isEmpty()) {
                        transports.add(value.trim());
                    }
                }
            }
            result.addProperty("recommendedTransport", transports.size() > 0 ? transports.get(0).getAsString() : "");
        } catch (Exception e) {
            System.err.println("AdtXmlParser.parseTransportCheck failed: " + e.getMessage());
        }

        return result;
    }

    /**
     * Parse the response of POST /sap/bc/adt/quickfixes/evaluation: the list of
     * quickfixes SAP can offer for a given ATC finding marker.
     */
    public static JsonArray parseQuickfixEvaluations(String xml) {
        JsonArray results = new JsonArray();
        if (isBlank(xml)) return results;

        try {
            Document doc = parseDocument(xml);
            NodeList allElements = doc.getElementsByTagName("*");
            for (int i = 0; i < allElements.getLength(); i++) {
                Element el = (Element) allElements.item(i);
                String local = el.getLocalName() != null ? el.getLocalName() : el.getTagName();
                if (!"evaluationResult".equals(local)) continue;

                JsonObject item = new JsonObject();
                String quickfixId = attr(el, "adtcore:type", attr(el, "type", ""));
                String name = attr(el, "adtcore:name", attr(el, "name", ""));
                String description = attr(el, "adtcore:description", attr(el, "description", ""));
                String uri = "";

                NodeList refs = el.getElementsByTagName("adtcore:objectReference");
                if (refs.getLength() == 0) refs = el.getElementsByTagName("objectReference");
                if (refs.getLength() > 0) {
                    Element ref = (Element) refs.item(0);
                    quickfixId = attr(ref, "adtcore:type", quickfixId);
                    name = attr(ref, "adtcore:name", name);
                    description = attr(ref, "adtcore:description", description);
                    uri = attr(ref, "adtcore:uri", "");
                }

                item.addProperty("quickfixId", quickfixId);
                item.addProperty("name", name);
                item.addProperty("description", description);
                item.addProperty("uri", uri);
                results.add(item);
            }
        } catch (Exception e) {
            System.err.println("AdtXmlParser.parseQuickfixEvaluations failed: " + e.getMessage());
        }

        return results;
    }

    /**
     * Parse the response of POST /sap/bc/adt/quickfixes/proposals/providers/atc/quickfixes/{id}:
     * the proposed fixed source plus any status messages SAP returned alongside it.
     * This never writes the proposal back -- callers decide whether to apply it via sap_set_source.
     */
    public static JsonObject parseQuickfixProposal(String xml) {
        JsonObject result = new JsonObject();
        result.addProperty("proposedSource", "");
        JsonArray messages = new JsonArray();
        result.add("statusMessages", messages);

        if (isBlank(xml)) {
            result.addProperty("success", false);
            return result;
        }

        try {
            Document doc = parseDocument(xml);

            StringBuilder sb = new StringBuilder();
            NodeList contentNodes = doc.getElementsByTagName("content");
            for (int i = 0; i < contentNodes.getLength(); i++) {
                if (sb.length() > 0) sb.append("\n");
                sb.append(contentNodes.item(i).getTextContent());
            }
            result.addProperty("proposedSource", sb.toString());

            boolean success = false;
            NodeList statusNodes = doc.getElementsByTagName("statusMessage");
            for (int i = 0; i < statusNodes.getLength(); i++) {
                Element el = (Element) statusNodes.item(i);
                String severity = attr(el, "severity", "");
                String message = attr(el, "message", el.getTextContent());
                JsonObject m = new JsonObject();
                m.addProperty("severity", severity);
                m.addProperty("message", message);
                messages.add(m);
                if ("info".equalsIgnoreCase(severity) && message != null
                        && message.toLowerCase().contains("success")) {
                    success = true;
                }
            }

            result.addProperty("success", success || sb.length() > 0);
        } catch (Exception e) {
            System.err.println("AdtXmlParser.parseQuickfixProposal failed: " + e.getMessage());
        }

        return result;
    }

    /**
     * Parse the final result of a batch activation run (/sap/bc/adt/activation/runs).
     * SAP returns the same "chkl:messages" shape used by single-object activation, but
     * each &lt;msg&gt; here additionally carries an objDescr/href telling which of the
     * batched objects it refers to, and the text sits in nested &lt;txt&gt; elements
     * instead of the element body directly.
     */
    public static JsonObject parseBatchActivationResult(String xml) {
        JsonObject result = new JsonObject();
        JsonArray messages = new JsonArray();
        result.add("messages", messages);
        result.addProperty("success", true);

        if (isBlank(xml)) return result;

        try {
            Document doc = parseDocument(xml);
            NodeList allElements = doc.getElementsByTagName("*");

            Boolean activationExecuted = null;
            for (int i = 0; i < allElements.getLength(); i++) {
                Element el = (Element) allElements.item(i);
                String local = el.getLocalName() != null ? el.getLocalName() : el.getTagName();
                if (!local.toLowerCase().contains("properties")) continue;
                NamedNodeMap attrs = el.getAttributes();
                for (int j = 0; j < attrs.getLength(); j++) {
                    Attr a = (Attr) attrs.item(j);
                    if (a.getName().toLowerCase().contains("activationexecuted")) {
                        activationExecuted = Boolean.parseBoolean(a.getValue());
                    }
                }
            }

            boolean hasError = false;
            for (int i = 0; i < allElements.getLength(); i++) {
                Element el = (Element) allElements.item(i);
                String local = el.getLocalName() != null ? el.getLocalName() : el.getTagName();
                if (!"msg".equalsIgnoreCase(local)) continue;

                String type = attr(el, "type", "I");
                String href = attr(el, "href", "");
                String objDescr = attr(el, "objDescr", "");

                StringBuilder text = new StringBuilder();
                NodeList txtNodes = el.getElementsByTagName("txt");
                for (int k = 0; k < txtNodes.getLength(); k++) {
                    String t = txtNodes.item(k).getTextContent();
                    if (t != null && !t.trim().isEmpty()) {
                        if (text.length() > 0) text.append(" - ");
                        text.append(t.trim());
                    }
                }
                if (text.length() == 0) {
                    String fallback = el.getTextContent();
                    if (fallback != null) text.append(fallback.trim());
                }

                String finalText = text.toString();
                if (!objDescr.isEmpty() && !finalText.contains(objDescr)) {
                    finalText = objDescr + ": " + finalText;
                }

                JsonObject msgObj = new JsonObject();
                msgObj.addProperty("text", finalText);
                msgObj.addProperty("severity", type);
                msgObj.addProperty("objectUri", href);
                messages.add(msgObj);

                if ("E".equalsIgnoreCase(type) || "A".equalsIgnoreCase(type)) {
                    hasError = true;
                }
            }

            boolean activationOk = activationExecuted == null || activationExecuted;
            result.addProperty("success", !hasError && activationOk);
            if (activationExecuted != null) {
                result.addProperty("activated", activationExecuted);
            }
        } catch (Exception e) {
            System.err.println("AdtXmlParser.parseBatchActivationResult failed: " + e.getMessage());
            result.addProperty("success", false);
            JsonObject errMsg = new JsonObject();
            errMsg.addProperty("text", "Parse error: " + e.getMessage());
            errMsg.addProperty("severity", "E");
            messages.add(errMsg);
        }

        return result;
    }

    /**
     * Scan an activation-run status payload for an Atom &lt;link&gt; whose rel/href
     * mentions "result" -- that is where SAP puts the final per-object messages once
     * a batch activation run finishes.
     */
    public static String extractResultLink(String xml) {
        if (isBlank(xml)) return null;
        try {
            Document doc = parseDocument(xml);
            NodeList allElements = doc.getElementsByTagName("*");
            for (int i = 0; i < allElements.getLength(); i++) {
                Element el = (Element) allElements.item(i);
                String local = el.getLocalName() != null ? el.getLocalName() : el.getTagName();
                if (!"link".equalsIgnoreCase(local)) continue;
                String rel = attr(el, "rel", "");
                String href = attr(el, "href", "");
                if (rel.toLowerCase().contains("result") || href.toLowerCase().contains("result")) {
                    return href;
                }
            }
        } catch (Exception e) {
            System.err.println("AdtXmlParser.extractResultLink failed: " + e.getMessage());
        }
        return null;
    }

    /**
     * Parse the response of GET /sap/bc/adt/cts/transportrequests: the user's own
     * transport organizer tree. SAP's tree XML has no single canonical element name
     * across releases, so this scans broadly for request-like nodes (matching the
     * heuristics the AWS ABAP Accelerator MCP uses) rather than locking onto one tag.
     */
    public static JsonArray parseTransportRequestsList(String xml) {
        JsonArray transports = new JsonArray();
        if (isBlank(xml)) return transports;

        try {
            Document doc = parseDocument(xml);
            String[] candidateTags = {
                "transportRequest", "request", "transport", "task", "workbenchRequest", "customizingRequest"
            };

            java.util.Set<Element> matched = new java.util.LinkedHashSet<>();
            for (String tag : candidateTags) {
                NodeList nodes = doc.getElementsByTagName("*");
                for (int i = 0; i < nodes.getLength(); i++) {
                    Element el = (Element) nodes.item(i);
                    String local = el.getLocalName() != null ? el.getLocalName() : el.getTagName();
                    if (local.equalsIgnoreCase(tag)) {
                        matched.add(el);
                    }
                }
            }

            if (matched.isEmpty()) {
                // Fallback: any element with a number/id-like attribute that looks like a TRKORR
                // (e.g. starts with a system prefix such as S4H/DEV, or is unusually long).
                NodeList all = doc.getElementsByTagName("*");
                for (int i = 0; i < all.getLength(); i++) {
                    Element el = (Element) all.item(i);
                    String number = attr(el, "number", attr(el, "id", ""));
                    if (isTransportLike(number)) {
                        matched.add(el);
                    }
                }
            }

            for (Element el : matched) {
                JsonObject obj = new JsonObject();
                String number = attr(el, "number", attr(el, "id", attr(el, "trkorr", "")));
                obj.addProperty("number", number);
                obj.addProperty("description", attr(el, "desc", attr(el, "description", "")));
                obj.addProperty("owner", attr(el, "owner", attr(el, "user", "")));
                obj.addProperty("status", attr(el, "status", ""));
                obj.addProperty("target", attr(el, "target", ""));
                obj.addProperty("type", attr(el, "type", ""));
                transports.add(obj);
            }
        } catch (Exception e) {
            System.err.println("AdtXmlParser.parseTransportRequestsList failed: " + e.getMessage());
        }

        return transports;
    }

    /**
     * Parse a real (non-mocked) /sap/bc/adt/migration/analysis response. Note this
     * endpoint is not a documented/standard SAP ADT API -- it returns HTTP errors on
     * most backends, which is why sap_get_migration_analysis falls back to hardcoded
     * mock data on failure (ported deliberately from the AWS ABAP Accelerator MCP).
     */
    public static JsonObject parseMigrationAnalysis(String xml) {
        JsonObject result = new JsonObject();
        JsonArray issues = new JsonArray();
        JsonArray recommendations = new JsonArray();
        JsonArray dependencies = new JsonArray();
        result.add("compatibilityIssues", issues);
        result.add("migrationRecommendations", recommendations);
        result.addProperty("effortEstimate", "Unknown");
        result.add("dependencies", dependencies);

        if (isBlank(xml)) return result;

        try {
            Document doc = parseDocument(xml);

            NodeList issueNodes = doc.getElementsByTagName("issue");
            for (int i = 0; i < issueNodes.getLength(); i++) {
                Element el = (Element) issueNodes.item(i);
                JsonObject issue = new JsonObject();
                issue.addProperty("severity", attr(el, "severity", "INFO"));
                issue.addProperty("message", attr(el, "message", "Unknown issue"));
                String line = attr(el, "line", "");
                if (line.isEmpty()) {
                    issue.add("line", com.google.gson.JsonNull.INSTANCE);
                } else {
                    try {
                        issue.addProperty("line", Integer.parseInt(line));
                    } catch (NumberFormatException nfe) {
                        issue.add("line", com.google.gson.JsonNull.INSTANCE);
                    }
                }
                issues.add(issue);
            }

            NodeList recNodes = doc.getElementsByTagName("recommendation");
            for (int i = 0; i < recNodes.getLength(); i++) {
                String text = recNodes.item(i).getTextContent();
                recommendations.add(text != null && !text.trim().isEmpty() ? text.trim() : "No recommendation");
            }

            NodeList effortNodes = doc.getElementsByTagName("effortEstimate");
            if (effortNodes.getLength() > 0) {
                String text = effortNodes.item(0).getTextContent();
                result.addProperty("effortEstimate", text != null && !text.trim().isEmpty() ? text.trim() : "Unknown");
            }

            NodeList depNodes = doc.getElementsByTagName("dependency");
            for (int i = 0; i < depNodes.getLength(); i++) {
                Element el = (Element) depNodes.item(i);
                dependencies.add(attr(el, "name", "Unknown dependency"));
            }
        } catch (Exception e) {
            System.err.println("AdtXmlParser.parseMigrationAnalysis failed: " + e.getMessage());
        }

        return result;
    }

    /**
     * Generic Atom feed entry parser, shared by sap_get_revisions (GET the link with
     * rel="...relations/versions" found via parseObjectStructure) and sap_get_short_dumps
     * (GET /sap/bc/adt/runtime/dumps). Both endpoints return a plain atom:feed of atom:entry
     * elements -- this returns every field that is actually present instead of guessing a
     * fixed shape, since the exact attribute used for things like "version" or "dump id"
     * was not independently confirmed against a real backend.
     */
    public static JsonArray parseAtomFeed(String xml) {
        JsonArray entries = new JsonArray();
        if (isBlank(xml)) return entries;

        try {
            Document doc = parseDocument(xml);
            NodeList entryNodes = doc.getElementsByTagNameNS(NS_ATOM, "entry");
            if (entryNodes.getLength() == 0) {
                entryNodes = doc.getElementsByTagName("entry");
            }

            for (int i = 0; i < entryNodes.getLength(); i++) {
                Element entry = (Element) entryNodes.item(i);
                JsonObject obj = new JsonObject();
                obj.addProperty("id", childText(entry, "id", ""));
                obj.addProperty("title", childText(entry, "title", ""));
                obj.addProperty("summary", childText(entry, "summary", ""));
                obj.addProperty("updated", childText(entry, "updated", ""));

                NodeList authorNodes = entry.getElementsByTagName("author");
                if (authorNodes.getLength() > 0) {
                    obj.addProperty("author", childText((Element) authorNodes.item(0), "name", ""));
                } else {
                    obj.addProperty("author", "");
                }

                NodeList contentNodes = entry.getElementsByTagName("content");
                obj.addProperty("contentSrc", contentNodes.getLength() > 0
                        ? attr((Element) contentNodes.item(0), "src", "") : "");

                JsonArray categories = new JsonArray();
                NodeList catNodes = entry.getElementsByTagName("category");
                for (int j = 0; j < catNodes.getLength(); j++) {
                    String term = attr((Element) catNodes.item(j), "term", "");
                    if (!term.isEmpty()) categories.add(term);
                }
                obj.add("categories", categories);

                JsonArray links = new JsonArray();
                NodeList linkNodes = entry.getElementsByTagName("link");
                for (int j = 0; j < linkNodes.getLength(); j++) {
                    Element link = (Element) linkNodes.item(j);
                    JsonObject linkObj = new JsonObject();
                    linkObj.addProperty("rel", attr(link, "rel", ""));
                    linkObj.addProperty("href", attr(link, "href", ""));
                    linkObj.addProperty("name", attr(link, "adtcore:name", ""));
                    links.add(linkObj);
                }
                obj.add("links", links);

                entries.add(obj);
            }
        } catch (Exception e) {
            System.err.println("AdtXmlParser.parseAtomFeed failed: " + e.getMessage());
        }

        return entries;
    }

    /**
     * Find the href of the link relation that exposes an object's version history
     * (rel="http://www.sap.com/adt/relations/versions"), as returned inside the
     * "links" array of parseObjectStructure(). Returns null if the object has no such link
     * (e.g. it genuinely has no revision history, or this ADT release doesn't expose one).
     */
    public static String findVersionsLink(JsonObject objectStructure) {
        if (objectStructure == null || !objectStructure.has("links")) return null;
        for (com.google.gson.JsonElement el : objectStructure.getAsJsonArray("links")) {
            JsonObject link = el.getAsJsonObject();
            String rel = link.has("rel") ? link.get("rel").getAsString() : "";
            if (rel.contains("relations/versions")) {
                return link.has("href") ? link.get("href").getAsString() : null;
            }
        }
        return null;
    }

    /**
     * Best-effort parser for GET .../source/main/enhancements (active enhancement
     * implementations on an object). SAP's exact "enh:" schema wasn't independently
     * confirmed against a real backend, so rather than guessing fixed field names this
     * collects every attribute actually present on each enhancement-like element --
     * namespace-agnostic, same defensive approach as parseAtcRunStatus/parseTransportCheck.
     */
    public static JsonArray parseEnhancements(String xml) {
        JsonArray results = new JsonArray();
        if (isBlank(xml)) return results;

        try {
            Document doc = parseDocument(xml);
            NodeList allElements = doc.getElementsByTagName("*");
            for (int i = 0; i < allElements.getLength(); i++) {
                Element el = (Element) allElements.item(i);
                String local = el.getLocalName() != null ? el.getLocalName() : el.getTagName();
                if (!local.toLowerCase().contains("enhancement") || local.toLowerCase().contains("enhancements")) {
                    continue;
                }
                JsonObject obj = new JsonObject();
                obj.addProperty("elementName", local);
                NamedNodeMap attrs = el.getAttributes();
                for (int j = 0; j < attrs.getLength(); j++) {
                    Attr a = (Attr) attrs.item(j);
                    obj.addProperty(a.getName(), a.getValue());
                }
                results.add(obj);
            }
        } catch (Exception e) {
            System.err.println("AdtXmlParser.parseEnhancements failed: " + e.getMessage());
        }

        return results;
    }

    /**
     * Parse GET /sap/bc/adt/abapgit/repos -- the list of abapGit repositories linked
     * in this system. Field names (key/package/url/branch_name/status/...) are the
     * documented abapgit.adt.repos.v2+xml shape used by the abap-adt-api reference client.
     */
    public static JsonArray parseAbapGitRepos(String xml) {
        JsonArray repos = new JsonArray();
        if (isBlank(xml)) return repos;

        try {
            Document doc = parseDocument(xml);
            NodeList repoNodes = doc.getElementsByTagName("repository");
            for (int i = 0; i < repoNodes.getLength(); i++) {
                Element repo = (Element) repoNodes.item(i);
                JsonObject obj = new JsonObject();
                obj.addProperty("key", childText(repo, "key", ""));
                obj.addProperty("packageName", childText(repo, "package", ""));
                obj.addProperty("url", childText(repo, "url", ""));
                obj.addProperty("branchName", childText(repo, "branch_name", ""));
                obj.addProperty("createdBy", childText(repo, "created_by", ""));
                obj.addProperty("createdAt", childText(repo, "created_at", ""));
                obj.addProperty("status", childText(repo, "status", ""));
                obj.addProperty("statusText", childText(repo, "status_text", ""));

                JsonArray links = new JsonArray();
                NodeList linkNodes = repo.getElementsByTagName("link");
                for (int j = 0; j < linkNodes.getLength(); j++) {
                    Element link = (Element) linkNodes.item(j);
                    JsonObject linkObj = new JsonObject();
                    linkObj.addProperty("rel", attr(link, "rel", ""));
                    linkObj.addProperty("href", attr(link, "href", ""));
                    linkObj.addProperty("type", attr(link, "type", ""));
                    links.add(linkObj);
                }
                obj.add("links", links);

                repos.add(obj);
            }
        } catch (Exception e) {
            System.err.println("AdtXmlParser.parseAbapGitRepos failed: " + e.getMessage());
        }

        return repos;
    }

    /**
     * Parse the abapGit "stage" response (unstaged/staged/ignored objects pending
     * pull/push for a repo). Generic attribute capture per object, same reasoning as
     * parseEnhancements -- the exact attribute names weren't independently confirmed.
     */
    public static JsonObject parseAbapGitStage(String xml) {
        JsonObject result = new JsonObject();
        JsonArray unstaged = new JsonArray();
        JsonArray staged = new JsonArray();
        JsonArray ignored = new JsonArray();
        result.add("unstagedObjects", unstaged);
        result.add("stagedObjects", staged);
        result.add("ignoredObjects", ignored);

        if (isBlank(xml)) return result;

        try {
            Document doc = parseDocument(xml);
            collectAbapGitObjects(doc, "unstaged_objects", unstaged);
            collectAbapGitObjects(doc, "staged_objects", staged);
            collectAbapGitObjects(doc, "ignored_objects", ignored);
            result.addProperty("unstagedCount", unstaged.size());
            result.addProperty("stagedCount", staged.size());
            result.addProperty("ignoredCount", ignored.size());
        } catch (Exception e) {
            System.err.println("AdtXmlParser.parseAbapGitStage failed: " + e.getMessage());
        }

        return result;
    }

    private static void collectAbapGitObjects(Document doc, String containerLocalName, JsonArray target) {
        NodeList allElements = doc.getElementsByTagName("*");
        for (int i = 0; i < allElements.getLength(); i++) {
            Element el = (Element) allElements.item(i);
            String local = el.getLocalName() != null ? el.getLocalName() : el.getTagName();
            if (!local.equalsIgnoreCase(containerLocalName)) continue;

            NodeList children = el.getChildNodes();
            for (int j = 0; j < children.getLength(); j++) {
                if (!(children.item(j) instanceof Element)) continue;
                Element child = (Element) children.item(j);
                JsonObject obj = new JsonObject();
                NamedNodeMap attrs = child.getAttributes();
                for (int k = 0; k < attrs.getLength(); k++) {
                    Attr a = (Attr) attrs.item(k);
                    obj.addProperty(a.getName(), a.getValue());
                }
                target.add(obj);
            }
        }
    }

    /**
     * Parse POST /sap/bc/adt/repository/nodestructure -- the package/object tree
     * SAP GUI's Project Explorer is built from. Rows arrive as SEU_ADT_REPOSITORY_OBJ_NODE
     * elements inside asx:abap/asx:values/DATA/TREE_CONTENT. If that shape isn't found
     * (different release), falls back to generic leaf-text scanning instead of returning
     * an empty/fabricated result.
     */
    public static JsonArray parseNodeStructure(String xml) {
        JsonArray nodes = new JsonArray();
        if (isBlank(xml)) return nodes;

        try {
            Document doc = parseDocument(xml);
            NodeList rowNodes = doc.getElementsByTagName("SEU_ADT_REPOSITORY_OBJ_NODE");
            for (int i = 0; i < rowNodes.getLength(); i++) {
                Element row = (Element) rowNodes.item(i);
                JsonObject obj = new JsonObject();
                obj.addProperty("objectType", childText(row, "OBJECT_TYPE", ""));
                obj.addProperty("objectName", childText(row, "OBJECT_NAME", ""));
                obj.addProperty("techName", childText(row, "TECH_NAME", ""));
                obj.addProperty("description", childText(row, "DESCRIPTION", ""));
                obj.addProperty("uri", childText(row, "OBJECT_URI", ""));
                obj.addProperty("expandable", childText(row, "EXPANDABLE", ""));
                nodes.add(obj);
            }

            if (nodes.size() == 0) {
                NodeList treeNodes = doc.getElementsByTagName("TREE_CONTENT");
                if (treeNodes.getLength() > 0) {
                    NodeList rows = treeNodes.item(0).getChildNodes();
                    for (int i = 0; i < rows.getLength(); i++) {
                        if (!(rows.item(i) instanceof Element)) continue;
                        Element row = (Element) rows.item(i);
                        JsonObject obj = new JsonObject();
                        NodeList fields = row.getChildNodes();
                        for (int j = 0; j < fields.getLength(); j++) {
                            if (!(fields.item(j) instanceof Element)) continue;
                            Element field = (Element) fields.item(j);
                            String text = field.getTextContent();
                            obj.addProperty(field.getTagName(), text != null ? text.trim() : "");
                        }
                        nodes.add(obj);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("AdtXmlParser.parseNodeStructure failed: " + e.getMessage());
        }

        return nodes;
    }

    private static boolean isTransportLike(String value) {
        if (value == null || value.isEmpty()) return false;
        String upper = value.toUpperCase();
        return upper.startsWith("S4H") || upper.startsWith("DEV") || value.length() > 5;
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
