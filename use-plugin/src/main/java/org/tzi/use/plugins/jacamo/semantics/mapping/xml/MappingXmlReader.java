package org.tzi.use.plugins.jacamo.semantics.mapping.xml;

import java.io.*;
import java.util.*;
import javax.xml.XMLConstants;
import javax.xml.parsers.*;
import org.xml.sax.*;
import org.xml.sax.helpers.DefaultHandler;
import org.tzi.use.plugins.jacamo.codegrounded.constraint.ExternalOclConstraintService;
import static org.tzi.use.plugins.jacamo.semantics.mapping.xml.MappingSpecification.*;

/** Bounded reader for this versioned declarative dialect. No script execution or URL resolution. */
public final class MappingXmlReader {
    public static final String NAMESPACE = "urn:use:jacamo-mapping:2";
    public static final int MAX_BYTES = 1_048_576;

    public MappingSpecification parse(byte[] bytes, String sourceName) {
        if (bytes == null || bytes.length > MAX_BYTES) throw new Location(sourceName, 1, 1, "").error("BYTE_CAPACITY");
        var handler = new Handler(sourceName);
        try {
            // USE also carries a legacy JAXP provider; choose the supported JDK 21
            // provider explicitly so security properties cannot be silently dropped.
            SAXParserFactory factory = SAXParserFactory.newDefaultInstance();
            factory.setNamespaceAware(true); factory.setXIncludeAware(false);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            XMLReader reader = factory.newSAXParser().getXMLReader();
            reader.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            reader.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            reader.setContentHandler(handler); reader.setErrorHandler(handler);
            reader.setEntityResolver((publicId, systemId) -> { throw handler.where().error("EXTERNAL_ENTITY_FORBIDDEN"); });
            reader.parse(new InputSource(new ByteArrayInputStream(bytes)));
            if (handler.root == null) throw handler.where().error("ROOT_REQUIRED");
            var spec = new MappingSpecification(ExternalOclConstraintService.sha256(bytes), handler.root, handler.namespaces);
            MappingVocabulary.validate(spec);
            return spec;
        } catch (SAXParseException error) {
            throw new Location(sourceName, error.getLineNumber(), error.getColumnNumber(), "").error("SYNTAX:" + error.getMessage());
        } catch (SAXException | ParserConfigurationException | IOException error) {
            throw new IllegalArgumentException("MAPPING_XML:" + sourceName + ":1:1:PARSER_UNAVAILABLE_OR_INVALID", error);
        }
    }

    private static final class Builder {
        final String name; final Map<String, String> attributes; final Location location;
        final StringBuilder text = new StringBuilder(); final List<Node> children = new ArrayList<>();
        Builder(String name, Map<String, String> attributes, Location location) { this.name = name; this.attributes = attributes; this.location = location; }
        Node build() { return new Node(name, attributes, text.toString().strip(), children, location); }
    }
    private static final class Handler extends DefaultHandler {
        final String source; final Deque<Builder> stack = new ArrayDeque<>(); Locator locator; Node root; int count;
        final Map<String, String> namespaces = new LinkedHashMap<>();
        Handler(String source) { this.source = source; }
        @Override public void setDocumentLocator(Locator locator) { this.locator = locator; }
        @Override public void startPrefixMapping(String prefix, String uri) {
            if (!stack.isEmpty() || namespaces.putIfAbsent(prefix, uri) != null) throw where().error("NAMESPACE_REDECLARATION_UNSUPPORTED");
        }
        Location where() { return new Location(source, locator == null ? 1 : locator.getLineNumber(), locator == null ? 1 : locator.getColumnNumber(), stack.isEmpty() ? "" : stack.peek().location.ruleId()); }
        @Override public void startElement(String uri, String name, String qName, Attributes attributes) {
            if (!NAMESPACE.equals(uri)) throw where().error("NAMESPACE_UNSUPPORTED:" + uri);
            if (++count > 4096 || stack.size() >= 64 || attributes.getLength() > 32) throw where().error("STRUCTURAL_CAPACITY");
            var values = new LinkedHashMap<String, String>();
            for (int i = 0; i < attributes.getLength(); i++) {
                if (!attributes.getURI(i).isEmpty()) throw where().error("QUALIFIED_ATTRIBUTE_UNSUPPORTED");
                if (attributes.getValue(i).length() > 65536) throw where().error("ATTRIBUTE_CAPACITY");
                values.put(attributes.getLocalName(i), attributes.getValue(i));
            }
            Location current = where();
            var located = new Location(source, current.line(), current.column(), values.getOrDefault("id", current.ruleId()));
            stack.push(new Builder(name, values, located));
        }
        @Override public void endElement(String uri, String localName, String qName) {
            Node node = stack.pop().build();
            if (stack.isEmpty()) { if (root != null) throw node.location().error("MULTIPLE_ROOTS"); root = node; }
            else stack.peek().children.add(node);
        }
        @Override public void characters(char[] chars, int start, int length) {
            if (stack.isEmpty()) return;
            var text = stack.peek().text;
            if (text.length() + length > 65536) throw where().error("TEXT_CAPACITY");
            text.append(chars, start, length);
        }
        @Override public void processingInstruction(String target, String data) { throw where().error("PROCESSING_INSTRUCTION_UNSUPPORTED"); }
        @Override public void error(SAXParseException error) throws SAXException { throw error; }
        @Override public void fatalError(SAXParseException error) throws SAXException { throw error; }
    }
}
