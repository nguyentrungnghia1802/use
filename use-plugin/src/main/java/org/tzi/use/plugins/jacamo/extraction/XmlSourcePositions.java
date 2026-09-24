package org.tzi.use.plugins.jacamo.extraction;

import java.io.StringReader;
import java.util.ArrayDeque;
import java.util.ArrayList;
import javax.xml.parsers.SAXParserFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.Locator;
import org.xml.sax.helpers.DefaultHandler;

/** Secure SAX locations attached to the already parsed DOM; no approximate ID lookup. */
final class XmlSourcePositions {
    static void attach(Document document, String source) throws Exception {
        var factory = SAXParserFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        var nodes = document.getElementsByTagName("*");
        var starts = new ArrayList<Integer>(); starts.add(0);
        for (int i = 0; i < source.length(); i++) if (source.charAt(i) == '\n') starts.add(i + 1);
        factory.newSAXParser().parse(new InputSource(new StringReader(source)), new DefaultHandler() {
            Locator locator; int index;
            final ArrayDeque<Element> stack = new ArrayDeque<>();
            @Override public void setDocumentLocator(Locator value) { locator = value; }
            int offset() { return Math.min(source.length(), starts.get(locator.getLineNumber() - 1) + locator.getColumnNumber() - 1); }
            @Override public void startElement(String uri, String local, String name, Attributes attributes) {
                var node = (Element) nodes.item(index++);
                if (!node.getTagName().equals(name)) throw new IllegalStateException("XML location traversal mismatch");
                int start = source.lastIndexOf('<', Math.max(0, offset() - 1));
                int line = java.util.Collections.binarySearch(starts, start);
                if (line < 0) line = -line - 2;
                node.setUserData("sourceOffset", start, null);
                node.setUserData("sourceLine", line + 1, null);
                node.setUserData("sourceColumn", start - starts.get(line) + 1, null);
                stack.push(node);
            }
            @Override public void endElement(String uri, String local, String name) {
                var node = stack.pop();
                node.setUserData("sourceText", source.substring((Integer) node.getUserData("sourceOffset"), offset()), null);
            }
        });
    }
}
