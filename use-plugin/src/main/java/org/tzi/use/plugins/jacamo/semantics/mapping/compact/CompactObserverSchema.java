package org.tzi.use.plugins.jacamo.semantics.mapping.compact;

import java.util.*;
import org.tzi.use.api.*;
import org.tzi.use.semantic.engine.use75.*;
import org.tzi.use.uml.mm.*;
import org.tzi.use.uml.ocl.type.*;

/** Direct typed native schema. No legacy document, recipe or expression bodies. */
public final class CompactObserverSchema {
    public static final String ANNOTATION="CompactObserverView";
    private final List<CompactMappingSpecification.ObserverView> views;
    private final String hash;
    public CompactObserverSchema(List<CompactMappingSpecification.ObserverView> views,String hash) {
        this.views=List.copyOf(views);this.hash=hash;var names=new HashSet<String>();var ids=new HashSet<String>();
        for(var view:views) {
            if(!Set.of("SOURCE_INSTANCE/1.0","NPL_GOAL_OBLIGATIONS/1.0").contains(view.population()))throw view.location().error("COMPACT_OBSERVER_POPULATION_UNSUPPORTED");
            if(!"CLASS".equals(view.category())||!identifier(view.name())||!names.add(view.name())||!ids.add(view.id())||view.fields().isEmpty())throw view.location().error("COMPACT_OBSERVER_CLASS_IDENTITY_REQUIRED");
            if(!(SourceReference.parse(view.source()) instanceof SourceReference.ArtifactRef||SourceReference.parse(view.source()) instanceof SourceReference.MoiseSchemeRef))throw view.location().error("COMPACT_OBSERVER_QUALIFIED_SOURCE_REQUIRED");
        }
        for(var view:views){var fields=new HashSet<String>();for(var field:view.fields()) {
            if(!identifier(field.name())||!fields.add(field.name())||field.origin()==null||field.origin().isBlank())throw field.location().error("COMPACT_OBSERVER_FIELD_IDENTITY_REQUIRED");
            if(!names.contains(field.type()))type(field.type());
            var binding=CompactObserverBindings.parse(view,field);
            if(binding.kind()==CompactObserverBindings.Kind.NPL_MONITOR&&views.stream().noneMatch(v->v.id().equals(binding.arguments().getFirst())&&v.name().equals(field.type())&&v.source().equals(view.source())&&v.population().equals("SOURCE_INSTANCE/1.0")))throw field.location().error("COMPACT_NPL_REFERENCE_TARGET_UNRESOLVED");
        }}
    }
    public List<CompactMappingSpecification.ObserverView> views(){return views;}
    public Map<String,String> classViews(){var r=new TreeMap<String,String>();views.forEach(v->r.put(v.id(),v.name()));return Map.copyOf(r);}
    public void install(UseModelApi api,Use75ModelBinding binding)throws UseApiException {
        if(api.getModel()!=binding.model())throw new IllegalArgumentException("COMPACT_OBSERVER_MODEL_OWNER_MISMATCH");
        for(var view:views)if(binding.model().getClassifier(view.name())!=null)throw view.location().error("COMPACT_OBSERVER_CLASS_COLLISION");
        for(var view:views) {
            var cls=api.createClass(view.name(),false);cls.addAnnotation(annotation(view,null));
            cls.addAnnotation(new MElementAnnotation(Use75CslModelBackend.HELPER,Map.of("kind","EXTERNAL_OBSERVER_VIEW","id64",Use75CslModelBackend.encode(view.id()))));
        }
        for(var view:views)for(var field:view.fields()) {
            if(classViews().containsValue(field.type())) {
                api.createAssociation(association(view,field),view.name(),"owners_"+safe(view.id())+"_"+field.name(),"*",0,field.type(),field.name(),"0..1",0);
                binding.model().getAssociation(association(view,field)).addAnnotation(annotation(view,field));
            } else api.createAttributeEx(binding.model().getClass(view.name()),field.name(),type(field.type())).addAnnotation(annotation(view,field));
        }
        binding.model().addAnnotation(new MElementAnnotation(ANNOTATION,Map.of("version","1.0","mappingHash",hash)));
    }
    private MElementAnnotation annotation(CompactMappingSpecification.ObserverView view,CompactMappingSpecification.ObserverView.ObserverField field) {
        var data=new TreeMap<String,String>();data.put("version","1.0");data.put("mappingHash",hash);data.put("view",view.id());data.put("source",view.source());
        if(field!=null){data.put("field",field.name());data.put("origin",field.origin());}return new MElementAnnotation(ANNOTATION,data);
    }
    public static String association(CompactMappingSpecification.ObserverView view,CompactMappingSpecification.ObserverView.ObserverField field){return "CompactObserver_"+safe(view.id())+"_"+field.name();}
    private static String safe(String name){return name.replaceAll("[^A-Za-z0-9_]","_");}
    private static boolean identifier(String value){return value!=null&&value.matches("[A-Za-z_][A-Za-z0-9_]*");}
    public static Type type(String name) {
        return switch(name){case "Integer"->TypeFactory.mkInteger();case "String"->TypeFactory.mkString();case "Boolean"->TypeFactory.mkBoolean();case "Set(String)"->TypeFactory.mkSet(TypeFactory.mkString());case "Sequence(String)"->TypeFactory.mkSequence(TypeFactory.mkString());default->throw new IllegalArgumentException("COMPACT_OBSERVER_FIELD_TYPE_UNSUPPORTED:"+name);};
    }
}
