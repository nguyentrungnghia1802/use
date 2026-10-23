package org.tzi.use.plugins.jacamo.semantics.mapping.auction;
import java.util.*;
import org.tzi.use.api.*;
import org.tzi.use.semantic.engine.use75.Use75ModelBinding;
import org.tzi.use.uml.ocl.type.TupleType;
import org.tzi.use.plugins.jacamo.semantics.mapping.ocl.ObserverSchema;
import org.tzi.use.plugins.jacamo.semantics.mapping.xml.MappingSpecification;

/** Legacy category policy over the shared native observer-schema implementation. */
public final class AuctionObserverUseBinding {
    public static final String ANNOTATION="AuctionObserverView",VERSION="1.0";
    private final ObserverSchema schema;
    public AuctionObserverUseBinding(AuctionMappingSpecification specification) {
        if(!VERSION.equals(specification.root().attr("formatVersion")))throw specification.root().location().error("OBSERVER_SCHEMA_VERSION_UNSUPPORTED");
        var support=specification.root().child("observerSupport");
        if(!support.attr("layer").equals("outside-CSL")||!support.attr("createsCSLClassifiers").equals("false")
                ||!support.attr("createsCSLAssociations").equals("false"))throw support.location().error("OBSERVER_SCHEMA_BOUNDARY_UNSUPPORTED");
        var nodes=new LinkedHashMap<String,AuctionMappingSpecification.Node>();
        var names=new HashSet<String>();
        for(var view:specification.views()) {
            if(!view.attr("sourceClassExists").equals("false")||!identifier(view.attr("name"))||!names.add(view.attr("name")))
                throw view.location().error("OBSERVER_VIEW_NAME_OR_SOURCE_CLASS_UNSUPPORTED");
            nodes.put(view.attr("id"),view);
        }

        var classIds=new LinkedHashSet<String>();var valueIds=new LinkedHashSet<String>();
        var bindings=specification.root().child("verificationBindings");
        bindings.children("externalInvariant").forEach(n->classIds.add(n.attr("observerViewRef")));
        bindings.children("taskBinding").forEach(n->valueIds.add(n.attr("observerViewRef")));
        var declared=new HashSet<>(classIds);declared.addAll(valueIds);
        if(!declared.equals(nodes.keySet())||classIds.stream().anyMatch(valueIds::contains))
            throw support.location().error("OBSERVER_VIEW_EXECUTION_CATEGORY_UNRESOLVED");

        schema=new ObserverSchema(neutral(specification.root()),specification.views().stream().map(AuctionObserverUseBinding::neutral).toList(),classIds,valueIds,specification.sourceHash(),ANNOTATION,VERSION);
    }
    public ObserverSchema schema(){return schema;}
    public Map<String,String> classViews(){return schema.classViews();}
    public String referenceAssociation(String view,String field){return schema.referenceAssociation(view,field);}
    public TupleType valueType(String view){return schema.valueType(view);}
    public void install(UseModelApi api,Use75ModelBinding binding)throws UseApiException{schema.install(api,binding);}
    private static boolean identifier(String value){return value.matches("[A-Za-z_][A-Za-z0-9_]*");}
    private static MappingSpecification.Node neutral(AuctionMappingSpecification.Node node) {
        var at=node.location();return new MappingSpecification.Node(node.name(),node.attributes(),node.text(),node.children().stream().map(AuctionObserverUseBinding::neutral).toList(),new MappingSpecification.Location(at.source(),at.line(),at.column(),at.ruleId()));
    }
}
