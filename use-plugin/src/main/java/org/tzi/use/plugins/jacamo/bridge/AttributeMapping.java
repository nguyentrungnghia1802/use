package org.tzi.use.plugins.jacamo.bridge;

import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.*;

/** A data correspondence only. This format deliberately has no plan/Task/iStar selectors. */
public record AttributeMapping(List<Attribute> attributes) {
    public static final String NAMESPACE="urn:use:jacamo-state-mapping:1";
    private static final Pattern SOURCE=Pattern.compile("(artifact|agent|organization|group):([A-Za-z_$][A-Za-z0-9_$.-]*)/(property|identity):([A-Za-z_$][A-Za-z0-9_$]*)");
    private static final Pattern TARGET=Pattern.compile("([A-Za-z_][A-Za-z0-9_]*)\\.([A-Za-z_][A-Za-z0-9_]*)");
    public record Attribute(String sourceCategory,String sourceClass,String sourceKind,String sourceName,String targetClass,String targetName) { }
    public AttributeMapping {attributes=List.copyOf(attributes);if(attributes.isEmpty())throw new IllegalArgumentException("ATTRIBUTE_MAPPING_EMPTY");}
    public static AttributeMapping read(Path path)throws Exception {
        if(Files.size(path)>1024*1024)throw new IllegalArgumentException("MAPPING_TOO_LARGE");
        var factory=DocumentBuilderFactory.newDefaultInstance();factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD,"");factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA,"");
        var root=factory.newDocumentBuilder().parse(path.toFile()).getDocumentElement();
        require(root,"mapping");attrs(root,Set.of());
        var entries=new ArrayList<Attribute>();var targets=new HashSet<String>();var owners=new HashMap<String,String>();
        for(Node n=root.getFirstChild();n!=null;n=n.getNextSibling()) {
            if(n.getNodeType()==Node.TEXT_NODE){if(!n.getTextContent().isBlank())throw new IllegalArgumentException("MAPPING_TEXT_NOT_ALLOWED");continue;}
            if(n.getNodeType()==Node.COMMENT_NODE)continue;
            if(!(n instanceof Element e))throw new IllegalArgumentException("MAPPING_ELEMENT_REQUIRED");
            require(e,"attribute");attrs(e,Set.of("source","target"));
            if(e.hasChildNodes())throw new IllegalArgumentException("ATTRIBUTE_MAPPING_MUST_BE_EMPTY");
            var source=SOURCE.matcher(e.getAttribute("source"));var target=TARGET.matcher(e.getAttribute("target"));
            if(!source.matches()||!target.matches())throw new IllegalArgumentException("ATTRIBUTE_REFERENCE_INVALID:"+e.getAttribute("source"));
            if(source.group(3).equals("identity")&&!Set.of("name","uuid","workspace").contains(source.group(4)))throw new IllegalArgumentException("IDENTITY_MEMBER_UNSUPPORTED");
            if(!targets.add(target.group()))throw new IllegalArgumentException("DUPLICATE_ATTRIBUTE_TARGET:"+target.group());
            String owner=source.group(1)+":"+source.group(2),old=owners.putIfAbsent(target.group(1),owner);
            if(old!=null&&!old.equals(owner))throw new IllegalArgumentException("AMBIGUOUS_CSL_INSTANCE_SOURCE:"+target.group(1));
            entries.add(new Attribute(source.group(1),source.group(2),source.group(3),source.group(4),target.group(1),target.group(2)));
        }
        return new AttributeMapping(entries);
    }
    public Set<String> sourceClasses(){var result=new TreeSet<String>();attributes.stream().filter(a->a.sourceCategory().equals("artifact")).forEach(a->result.add(a.sourceClass()));return Set.copyOf(result);}
    private static void require(Element e,String local){if(!NAMESPACE.equals(e.getNamespaceURI())||!local.equals(e.getLocalName()))throw new IllegalArgumentException("STATE_MAPPING_ELEMENT_INVALID:"+e.getTagName());}
    private static void attrs(Element e,Set<String> allowed){for(int i=0;i<e.getAttributes().getLength();i++){var a=e.getAttributes().item(i);if(XMLConstants.XMLNS_ATTRIBUTE_NS_URI.equals(a.getNamespaceURI()))continue;if(a.getNamespaceURI()!=null||!allowed.contains(a.getNodeName()))throw new IllegalArgumentException("STATE_MAPPING_ATTRIBUTE_INVALID:"+a.getNodeName());}}
}
