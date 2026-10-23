package org.tzi.use.plugins.jacamo.semantics.mapping.xml;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;
import javax.xml.XMLConstants;
import javax.xml.namespace.NamespaceContext;
import javax.xml.parsers.*;
import javax.xml.xpath.*;
import org.jacamo.bridge.contract.ModelSnapshot;
import org.jacamo.bridge.contract.semantic.MoiseSemanticContract;
import org.tzi.use.plugins.jacamo.codegrounded.constraint.ExternalOclConstraintService;
import org.tzi.use.plugins.jacamo.semantics.mapping.SourceModelNormalizer;
import org.w3c.dom.*;
import org.xml.sax.*;
import org.xml.sax.helpers.DefaultHandler;
import static org.tzi.use.plugins.jacamo.semantics.mapping.xml.MappingSpecification.Node;
import static org.tzi.use.plugins.jacamo.semantics.mapping.xml.SourceSelectors.Row;

/** Executes the pinned fragment selectors. Official parsers remain the source model authority. */
public final class SourceSelectors {
    private static final Pattern GROUP = Pattern.compile("\\(\\?P<([A-Za-z_][A-Za-z0-9_]*)>");
    private static final int MAX_ROWS = 4096, MAX_REGEX = 8192, MAX_WORK = 2_000_000;

    public record Row(String selectorId,String sourceId,String sourceDigest,Map<String,String> values,
                      int line,int column,String locator,String matched) {
        public Row {values=Collections.unmodifiableMap(new LinkedHashMap<>(values));}
    }
    /** Unit-level fragment evaluation; this alone grants no frozen bundle/program admission. */
    public List<Row> select(Node selector, byte[] bytes, Map<String, String> namespaces) {
        if (bytes.length > 8 * 1024 * 1024) throw selector.location().error("SOURCE_CAPACITY");
        int expected = Integer.parseInt(selector.attr("expectedCount"));
        if (expected > MAX_ROWS) throw selector.location().error("SELECTOR_ROW_CAPACITY");
        String digest = ExternalOclConstraintService.sha256(bytes);
        List<Row> rows = switch (selector.attr("syntax")) {
            case "regex" -> regex(selector, new String(bytes, StandardCharsets.UTF_8), digest);
            case "xpath" -> xpath(selector, bytes, digest, namespaces);
            default -> throw selector.location().error("SELECTOR_SYNTAX_UNSUPPORTED");
        };
        if (rows.size() != expected) throw selector.location().error("SELECTOR_COUNT_MISMATCH:" + selector.attr("id") + ":" + expected + ":" + rows.size());
        return List.copyOf(rows);
    }

    private List<Row> regex(Node selector, String original, String digest) {
        String expression = selector.child("pattern").text();
        if (expression.length() > MAX_REGEX || expression.contains("(?P=") || expression.contains("(?<=" ) || expression.contains("(?<!")) throw selector.location().error("REGEX_FEATURE_UNSUPPORTED");
        var captures = new LinkedHashMap<String, String>();
        var groupMatcher = GROUP.matcher(expression); var translated = new StringBuilder();
        while (groupMatcher.find()) {
            String name = groupMatcher.group(1), internal = "g" + captures.size();
            if (captures.size() >= 32 || captures.putIfAbsent(name, internal) != null) throw selector.location().error("REGEX_CAPTURE_AMBIGUOUS_OR_CAPACITY");
            groupMatcher.appendReplacement(translated, Matcher.quoteReplacement("(?<" + internal + ">"));
        }
        groupMatcher.appendTail(translated);
        String source = stripComments(original, selector);
        var result = new ArrayList<Row>();
        try {
            var pattern = Pattern.compile(translated.toString(), Pattern.MULTILINE | Pattern.UNICODE_CHARACTER_CLASS);
            var matcher = pattern.matcher(new WorkBudget(source, selector));
            while (matcher.find()) {
                if (result.size() >= MAX_ROWS || matcher.start() == matcher.end()) throw selector.location().error("REGEX_EMPTY_MATCH_OR_ROW_CAPACITY");
                var values = new LinkedHashMap<String, String>();
                for (var capture : captures.entrySet()) {
                    String value = matcher.group(capture.getValue());
                    if (value == null) throw selector.location().error("CAPTURE_MISSING:" + capture.getKey());
                    values.put(capture.getKey(), value);
                }
                int start = matcher.start(); while (start < matcher.end() && Character.isWhitespace(source.charAt(start))) start++;
                int line = 1, column = 1;
                for (int i = 0; i < start; i++) { if (source.charAt(i) == '\n') { line++; column = 1; } else column++; }
                result.add(new Row(selector.attr("id"), selector.attr("sourceRef"), digest, values, line, column,
                        "offset:" + start + ".." + matcher.end(), source.substring(start, matcher.end())));
            }
        } catch (PatternSyntaxException error) { throw selector.location().error("REGEX_INVALID:" + error.getDescription()); }
        catch (StackOverflowError error) { throw selector.location().error("REGEX_STACK_CAPACITY"); }
        return result;
    }

    private List<Row> xpath(Node selector, byte[] bytes, String digest, Map<String, String> namespaces) {
        try {
            Document document = locatedXml(bytes);
            var factory = XPathFactory.newDefaultInstance(); factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            var xpath = factory.newXPath();
            xpath.setNamespaceContext(new NamespaceContext() {
                @Override public String getNamespaceURI(String prefix) { return namespaces.getOrDefault(prefix, XMLConstants.NULL_NS_URI); }
                @Override public String getPrefix(String uri) { return namespaces.entrySet().stream().filter(e -> e.getValue().equals(uri)).map(Map.Entry::getKey).findFirst().orElse(null); }
                @Override public Iterator<String> getPrefixes(String uri) { return namespaces.entrySet().stream().filter(e -> e.getValue().equals(uri)).map(Map.Entry::getKey).iterator(); }
            });
            NodeList nodes = (NodeList) xpath.evaluate(selector.attr("expression"), document, XPathConstants.NODESET);
            if (nodes.getLength() > MAX_ROWS) throw selector.location().error("XPATH_ROW_CAPACITY");
            var result = new ArrayList<Row>();
            for (int i = 0; i < nodes.getLength(); i++) {
                org.w3c.dom.Node node = nodes.item(i); var values = new LinkedHashMap<String, String>();
                for (Node capture : selector.children("capture")) {
                    String value = xpath.evaluate(capture.attr("expression"), node);
                    if (value.isEmpty()) throw capture.location().error("CAPTURE_MISSING:" + capture.attr("name"));
                    values.put(capture.attr("name"), value);
                }
                int[] position = (int[]) node.getUserData("source-position");
                if (position == null) throw selector.location().error("XPATH_LOCATION_UNAVAILABLE");
                result.add(new Row(selector.attr("id"), selector.attr("sourceRef"), digest, values, position[0], position[1], selector.attr("expression") + "#" + i, node.getNodeName()));
            }
            return result;
        } catch (XPathExpressionException | XPathFactoryConfigurationException | ParserConfigurationException | SAXException | IOException error) {
            throw selector.location().error("XPATH_OR_SOURCE_XML_INVALID:" + error.getMessage());
        }
    }

    private static Document locatedXml(byte[] bytes) throws ParserConfigurationException, SAXException, IOException {
        Document document = DocumentBuilderFactory.newDefaultInstance().newDocumentBuilder().newDocument();
        var factory = SAXParserFactory.newDefaultInstance(); factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        var reader = factory.newSAXParser().getXMLReader();
        reader.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, ""); reader.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        var handler = new DefaultHandler() {
            final Deque<Element> stack = new ArrayDeque<>(); Locator locator; int count;
            @Override public void setDocumentLocator(Locator value) { locator = value; }
            @Override public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
                if (++count > 16384 || stack.size() >= 64) throw new SAXException("SOURCE_XML_CAPACITY");
                Element element = document.createElementNS(uri.isEmpty() ? null : uri, qName);
                for (int i = 0; i < attributes.getLength(); i++) element.setAttributeNS(attributes.getURI(i).isEmpty() ? null : attributes.getURI(i), attributes.getQName(i), attributes.getValue(i));
                element.setUserData("source-position", new int[]{locator.getLineNumber(), locator.getColumnNumber()}, null);
                if (stack.isEmpty()) document.appendChild(element); else stack.peek().appendChild(element);
                stack.push(element);
            }
            @Override public void endElement(String uri, String name, String qName) { stack.pop(); }
            @Override public void characters(char[] chars, int start, int length) { if (!stack.isEmpty()) stack.peek().appendChild(document.createTextNode(new String(chars, start, length))); }
            @Override public void error(SAXParseException error) throws SAXException { throw error; }
            @Override public void fatalError(SAXParseException error) throws SAXException { throw error; }
        };
        reader.setContentHandler(handler); reader.setErrorHandler(handler);
        reader.setEntityResolver((id, uri) -> { throw new SAXException("SOURCE_EXTERNAL_ENTITY_FORBIDDEN"); });
        reader.parse(new InputSource(new ByteArrayInputStream(bytes))); return document;
    }

    /** Replaces comment characters with spaces; preserves offsets, newlines and quoted literals. */
    private static String stripComments(String text, Node at) {
        var output = new StringBuilder(text); int state = 0; char quote = 0; boolean escaped = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i), next = i + 1 < text.length() ? text.charAt(i + 1) : 0;
            if (state == 1) { if (c == '\n' || c == '\r') state = 0; else output.setCharAt(i, ' '); continue; }
            if (state == 2) {
                if (c != '\n' && c != '\r') output.setCharAt(i, ' ');
                if (c == '*' && next == '/') { output.setCharAt(++i, ' '); state = 0; } continue;
            }
            if (quote != 0) { if (escaped) escaped = false; else if (c == '\\') escaped = true; else if (c == quote) quote = 0; continue; }
            if (c == '\'' || c == '"') { quote = c; continue; }
            if (c == '/' && (next == '/' || next == '*')) { output.setCharAt(i, ' '); output.setCharAt(++i, ' '); state = next == '/' ? 1 : 2; }
        }
        if (state == 2 || quote != 0) throw at.location().error("SOURCE_FRAGMENT_UNTERMINATED_COMMENT_OR_STRING");
        return output.toString();
    }

    private static final class WorkBudget implements CharSequence {
        final String text; final Node at; final int start, end; final int[] work;
        WorkBudget(String text, Node at) { this(text, at, 0, text.length(), new int[]{MAX_WORK}); }
        WorkBudget(String text, Node at, int start, int end, int[] work) { this.text = text; this.at = at; this.start = start; this.end = end; this.work = work; }
        @Override public int length() { return end - start; }
        @Override public char charAt(int index) { if (--work[0] < 0) throw at.location().error("REGEX_WORK_BUDGET"); return text.charAt(start + index); }
        @Override public CharSequence subSequence(int from, int to) { return new WorkBudget(text, at, start + from, start + to, work); }
        @Override public String toString() { return text.substring(start, end); }
    }
}
