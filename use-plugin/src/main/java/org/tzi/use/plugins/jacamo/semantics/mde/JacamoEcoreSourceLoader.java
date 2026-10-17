package org.tzi.use.plugins.jacamo.semantics.mde;

import java.io.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import org.eclipse.emf.common.util.*;
import org.eclipse.emf.ecore.*;
import org.eclipse.emf.ecore.resource.impl.*;
import org.eclipse.emf.ecore.util.*;
import org.eclipse.emf.ecore.xmi.impl.*;
import org.tzi.use.plugins.jacamo.codegrounded.constraint.ExternalOclConstraintService;
import org.tzi.use.plugins.jacamo.semantics.mapping.NormalizedSourceModel;
import org.tzi.use.plugins.jacamo.semantics.evidence.SemanticJson;

/** Read-only dynamic EMF frontend. All source fields/default origins and references are retained. */
public final class JacamoEcoreSourceLoader {
    public static final String RESOURCE="/org/tzi/use/plugins/jacamo/semantics/metamodel/jacamo_v2.ecore";
    public record Classifier(String name,String kind,List<String> features,List<String> literals){public Classifier{features=List.copyOf(features);literals=List.copyOf(literals);}}
    public record Metamodel(String namespace,String hash,List<Classifier> classifiers){public Metamodel{classifiers=List.copyOf(classifiers);}}
    public byte[] bundledMetamodel() {
        try(var stream=JacamoEcoreSourceLoader.class.getResourceAsStream(RESOURCE)){if(stream==null)throw new IllegalStateException("SOURCE_METAMODEL_RESOURCE_MISSING");return stream.readAllBytes();}
        catch(IOException e){throw new IllegalStateException("SOURCE_METAMODEL_READ_FAILED",e);}
    }
    public Metamodel inspect(byte[] bytes) {
        var pkg=loadPackage(bytes,newResources());
        var result=new ArrayList<Classifier>();
        for(var classifier:pkg.getEClassifiers())result.add(new Classifier(classifier.getName(),classifier instanceof EClass?"EClass":classifier instanceof EEnum?"EEnum":"EDataType",
            classifier instanceof EClass cls?cls.getEAllStructuralFeatures().stream().map(EStructuralFeature::getName).toList():List.of(),
            classifier instanceof EEnum enumeration?enumeration.getELiterals().stream().map(l->l.getName()+"="+l.getLiteral()+":"+l.getValue()).toList():List.of()));
        return new Metamodel(pkg.getNsURI(),ExternalOclConstraintService.sha256(bytes),result);
    }
    public NormalizedSourceModel load(byte[] metamodel,byte[] xmi) {
        var resource=definitionResource(metamodel,xmi);
        var lexical=lexicalFeatures(xmi,resource);
        String hash=ExternalOclConstraintService.sha256(xmi);var objects=new ArrayList<EObject>();var iterator=resource.getAllContents();while(iterator.hasNext())objects.add(iterator.next());
        validateRoleCycles(objects);
        var identities=new IdentityHashMap<EObject,String>();for(var object:objects)identities.put(object,"emf:"+hash+":"+resource.getURIFragment(object));
        var nodes=new ArrayList<NormalizedSourceModel.Node>();
        for(var object:objects) {
            var features=new TreeMap<String,NormalizedSourceModel.Feature>();
            for(var feature:object.eClass().getEAllStructuralFeatures()) {
                Object raw=object.eGet(feature,true);Object value;
                if(feature.isMany())value=((List<?>)raw).stream().map(v->value(v,identities)).toList();else value=value(raw,identities);
                boolean explicit=lexical.getOrDefault(object,Set.of()).contains(feature.getName());
                features.put(feature.getName(),new NormalizedSourceModel.Feature(value,explicit,explicit?"SOURCE_XMI":object.eIsSet(feature)?"EMF_DERIVED_REFERENCE":"METAMODEL_DEFAULT"));
            }
            nodes.add(new NormalizedSourceModel.Node(identities.get(object),object.eClass().getName(),NormalizedSourceModel.Layer.MODEL_DEFINITION,"EMF_MODEL_DEFINITION","org.eclipse.emf.ecore.EObject",identities.getOrDefault(object.eContainer(),""),features));
        }
        return new NormalizedSourceModel("EMF",hash,ExternalOclConstraintService.sha256(metamodel),nodes,List.of(
            "POSITIONAL_IDS_REQUIRE_TRACE_COMPLEMENT_ACROSS_EDITS","SOURCE_ABSTRACTION_INCOMPLETE_WITHOUT_OFFICIAL_SUPPLEMENTS","ECORE_ROLE_REFERENCES_ARE_DEFINITIONS_NOT_ACCEPTED_ENACTMENTS"));
    }
    private static org.eclipse.emf.ecore.resource.Resource definitionResource(byte[] metamodel,byte[] xmi) {
        safeXml(xmi);var resources=newResources();var pkg=loadPackage(metamodel,resources);resources.getPackageRegistry().put(pkg.getNsURI(),pkg);
        var resource=resources.createResource(URI.createURI("memory:/source.xmi"));
        try {resource.load(new ByteArrayInputStream(xmi),xmlOptions());}catch(IOException|RuntimeException e){throw new IllegalArgumentException("SOURCE_XMI_LOAD_FAILED",e);}
        resolve(resources);
        if(!resource.getErrors().isEmpty() || !EcoreUtil.UnresolvedProxyCrossReferencer.find(resources).isEmpty())throw new IllegalArgumentException("SOURCE_XMI_UNRESOLVED_OR_INVALID:"+resource.getErrors());
        for(var root:resource.getContents()) {
            var diagnostic=Diagnostician.INSTANCE.validate(root);
            if(diagnostic.getSeverity()>=Diagnostic.ERROR)throw new IllegalArgumentException("SOURCE_XMI_VALIDATION_FAILED:"+diagnostic);
        }
        return resource;
    }
    public record DefinitionEdit(String fragment,String feature,Object value){}
    public record DefinitionRewrite(byte[] bytes,Map<String,String> fragmentRebase) {
        public DefinitionRewrite{bytes=bytes.clone();fragmentRebase=Map.copyOf(fragmentRebase);}
        @Override public byte[] bytes(){return bytes.clone();}
    }
    /** Offline edit primitive; SourceLens owns the admitted fields and inverse policy. No filesystem writes. */
    public DefinitionRewrite rewrite(byte[] metamodel,byte[] xmi,List<DefinitionEdit> edits) {
        var resource=definitionResource(metamodel,xmi);var originals=new LinkedHashMap<String,EObject>();var all=resource.getAllContents();while(all.hasNext()){var obj=all.next();originals.put(resource.getURIFragment(obj),obj);}
        for(var edit:edits){var object=originals.get(edit.fragment());if(object==null)throw new IllegalArgumentException("SOURCE_EDIT_FRAGMENT_UNRESOLVED:"+edit.fragment());var feature=object.eClass().getEStructuralFeature(edit.feature());
            if(feature instanceof EAttribute attr && !feature.isMany())object.eSet(feature,attr.getEAttributeType().getEPackage().getEFactoryInstance().createFromString(attr.getEAttributeType(),(edit.value() instanceof Number number?new java.math.BigDecimal(number.toString()).toPlainString():String.valueOf(edit.value()))));
            else if(feature instanceof EReference ref && ref.isContainment() && feature.isMany() && edit.value() instanceof List<?> order){
                @SuppressWarnings("unchecked") var values=(EList<EObject>)object.eGet(feature);var byName=new HashMap<String,EObject>();for(var child:values){var id=child.eClass().getEStructuralFeature("id");if(id==null || byName.put(String.valueOf(child.eGet(id)),child)!=null)throw new IllegalArgumentException("SOURCE_CHILD_ID_AMBIGUOUS");}
                if(order.size()!=values.size() || new HashSet<>(order).size()!=values.size() || !byName.keySet().equals(new HashSet<>(order)))throw new IllegalArgumentException("SOURCE_REORDER_REQUIRES_EXACT_PERMUTATION");
                for(int i=0;i<order.size();i++)values.move(i,byName.get(order.get(i)));
            } else throw new IllegalArgumentException("SOURCE_EDIT_FEATURE_UNSUPPORTED:"+edit.feature());
        }
        validateRoleCycles(new ArrayList<>(originals.values()));var rebase=new TreeMap<String,String>();originals.forEach((fragment,obj)->rebase.put(fragment,resource.getURIFragment(obj)));
        try{var out=new ByteArrayOutputStream();resource.save(out,Map.of(org.eclipse.emf.ecore.xmi.XMLResource.OPTION_ENCODING,"UTF-8"));byte[] bytes=out.toByteArray();load(metamodel,bytes);return new DefinitionRewrite(bytes,rebase);}catch(IOException e){throw new IllegalArgumentException("SOURCE_EDIT_SERIALIZATION_FAILED",e);}
    }
    private static void validateRoleCycles(List<EObject> objects) {
        var done=Collections.newSetFromMap(new IdentityHashMap<EObject,Boolean>());
        for(var object:objects)if(object.eClass().getName().equals("Role"))visitRole(object,Collections.newSetFromMap(new IdentityHashMap<EObject,Boolean>()),done);
    }
    private static void visitRole(EObject role,Set<EObject> path,Set<EObject> done) {
        if(done.contains(role))return;if(!path.add(role))throw new IllegalArgumentException("SOURCE_ROLE_GENERALIZATION_CYCLE");
        if(path.size()>512)throw new IllegalArgumentException("SOURCE_ROLE_HIERARCHY_DEPTH_LIMIT");
        var feature=role.eClass().getEStructuralFeature("superRoles");
        if(feature==null || !feature.isMany() || !(feature instanceof EReference))throw new IllegalArgumentException("SOURCE_ROLE_SCHEMA_PROFILE_UNSUPPORTED");
        for(Object parent:(List<?>)role.eGet(feature))visitRole((EObject)parent,path,done);
        path.remove(role);done.add(role);
    }
    private static Object value(Object raw,Map<EObject,String> identities) {
        // Dynamic EEnumLiteral also implements EObject; enum values are not source object references.
        if(raw instanceof Enumerator enumeration)return Map.of("name",enumeration.getName(),"literal",enumeration.getLiteral(),"ordinal",enumeration.getValue());
        if(raw instanceof EObject object){String id=identities.get(object);if(id==null)throw new IllegalArgumentException("SOURCE_EXTERNAL_OBJECT_REFERENCE_NOT_ADMITTED");return Map.of("reference",id);}
        return SemanticJson.freeze(raw);
    }
    private static Map<EObject,Set<String>> lexicalFeatures(byte[] xml,org.eclipse.emf.ecore.resource.Resource resource) {
        try {
            var factory=javax.xml.parsers.DocumentBuilderFactory.newDefaultInstance();factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
            factory.setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_DTD,"");factory.setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_SCHEMA,"");
            var root=factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml)).getDocumentElement();
            var elements="XMI".equals(root.getLocalName()) && "http://www.omg.org/XMI".equals(root.getNamespaceURI())?children(root):List.of(root);
            if(elements.size()!=resource.getContents().size())throw new IllegalArgumentException("SOURCE_XML_ROOT_CORRESPONDENCE_UNSUPPORTED");
            var result=new IdentityHashMap<EObject,Set<String>>();
            for(int i=0;i<elements.size();i++)lexical(resource.getContents().get(i),elements.get(i),result);
            return result;
        }catch(javax.xml.parsers.ParserConfigurationException|org.xml.sax.SAXException|IOException e){throw new IllegalArgumentException("SOURCE_XML_ORIGIN_CAPTURE_FAILED",e);}
    }
    private static void lexical(EObject object,org.w3c.dom.Element element,Map<EObject,Set<String>> result) {
        var explicit=new HashSet<String>();var elements=children(element);
        for(var feature:object.eClass().getEAllStructuralFeatures()) {
            var nested=elements.stream().filter(e->feature.getName().equals(e.getLocalName())).toList();
            if(element.hasAttribute(feature.getName()) || !nested.isEmpty())explicit.add(feature.getName());
            if(feature instanceof EReference reference && reference.isContainment()) {
                Object raw=object.eGet(feature);List<?> values=feature.isMany()?(List<?>)raw:raw==null?List.of():List.of(raw);
                if(values.size()!=nested.size())throw new IllegalArgumentException("SOURCE_XML_CONTAINMENT_ORIGIN_UNSUPPORTED:"+feature.getName());
                for(int i=0;i<values.size();i++)lexical((EObject)values.get(i),nested.get(i),result);
            }
        }
        result.put(object,Set.copyOf(explicit));
    }
    private static List<org.w3c.dom.Element> children(org.w3c.dom.Element element) {
        var result=new ArrayList<org.w3c.dom.Element>();var nodes=element.getChildNodes();
        for(int i=0;i<nodes.getLength();i++)if(nodes.item(i) instanceof org.w3c.dom.Element child)result.add(child);
        return result;
    }
    private static ResourceSetImpl newResources() {
        var resources=new ResourceSetImpl();resources.getResourceFactoryRegistry().getExtensionToFactoryMap().put("ecore",new EcoreResourceFactoryImpl());resources.getResourceFactoryRegistry().getExtensionToFactoryMap().put("xmi",new XMIResourceFactoryImpl());
        resources.getPackageRegistry().put(EcorePackage.eNS_URI,EcorePackage.eINSTANCE);
        resources.setURIConverter(new ExtensibleURIConverterImpl(){@Override public InputStream createInputStream(URI uri,Map<?,?> options)throws IOException{throw new IOException("EXTERNAL_MODEL_RESOURCE_NOT_ADMITTED:"+uri);}});
        return resources;
    }
    private static void resolve(ResourceSetImpl resources){try{EcoreUtil.resolveAll(resources);}catch(RuntimeException e){throw new IllegalArgumentException("SOURCE_MODEL_REFERENCE_RESOLUTION_FAILED",e);}}
    private static EPackage loadPackage(byte[] bytes,ResourceSetImpl resources) {
        safeXml(bytes);var resource=resources.createResource(URI.createURI("memory:/metamodel.ecore"));
        try{resource.load(new ByteArrayInputStream(bytes),xmlOptions());}catch(IOException|RuntimeException e){throw new IllegalArgumentException("SOURCE_METAMODEL_LOAD_FAILED",e);}
        resolve(resources);
        if(resource.getContents().size()!=1 || !(resource.getContents().getFirst() instanceof EPackage pkg))throw new IllegalArgumentException("SOURCE_EPACKAGE_REQUIRED");
        var diagnostic=Diagnostician.INSTANCE.validate(pkg);
        if(!resource.getErrors().isEmpty() || !EcoreUtil.UnresolvedProxyCrossReferencer.find(resources).isEmpty() || diagnostic.getSeverity()>=Diagnostic.ERROR)throw new IllegalArgumentException("SOURCE_METAMODEL_INVALID:"+diagnostic);
        if(EcorePackage.eNS_URI.equals(pkg.getNsURI()))throw new IllegalArgumentException("SOURCE_NAMESPACE_COLLIDES_WITH_ECORE");
        return pkg;
    }
    private static Map<String,Object> xmlOptions(){return Map.of(org.eclipse.emf.ecore.xmi.XMLResource.OPTION_PARSER_FEATURES,Map.of(
        "http://apache.org/xml/features/disallow-doctype-decl",true,"http://xml.org/sax/features/external-general-entities",false,"http://xml.org/sax/features/external-parameter-entities",false));}
    private static void safeXml(byte[] bytes) {
        if(bytes==null || bytes.length>8*1024*1024)throw new IllegalArgumentException("SOURCE_XML_SIZE_LIMIT");
        String text=new String(bytes,StandardCharsets.UTF_8).toUpperCase(Locale.ROOT);
        if(text.contains("<!DOCTYPE") || text.contains("<!ENTITY"))throw new IllegalArgumentException("SOURCE_XML_EXTERNAL_ENTITIES_NOT_ADMITTED");
    }
}
