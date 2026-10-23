package org.tzi.use.plugins.jacamo.semantics.mapping.ocl;
import org.tzi.use.plugins.jacamo.semantics.mapping.xml.MappingSpecification;

import java.util.*;
import org.jacamo.bridge.contract.CanonicalJson;
import org.tzi.use.api.*;
import org.tzi.use.semantic.engine.use75.*;
import org.tzi.use.uml.mm.MElementAnnotation;
import org.tzi.use.uml.ocl.type.*;

/** XML-selected observer schema in the existing native model; no MSystem, values or history. */
public final class ObserverSchema {
    private final String annotation,version,sourceHash;
    private static final Set<String> SCALARS=Set.of("Integer","String","Boolean","Set(String)");
    private final MappingSpecification.Node root;
    private final Map<String,MappingSpecification.Node> views;
    private final Map<String,String> classViews;
    private final Map<String,TupleType> valueTypes;

    public ObserverSchema(MappingSpecification.Node root,List<MappingSpecification.Node> selected,
            Set<String> classIds,Set<String> valueIds,String sourceHash,String annotation,String version) {
        this.root=Objects.requireNonNull(root);this.sourceHash=sourceHash;this.annotation=annotation;this.version=version;
        var nodes=new LinkedHashMap<String,MappingSpecification.Node>();
        var names=new HashSet<String>();
        for(var view:selected) {
            if(!identifier(view.attr("name"))||!names.add(view.attr("name")))
                throw view.location().error("OBSERVER_VIEW_NAME_OR_SOURCE_CLASS_UNSUPPORTED");
            nodes.put(view.attr("id"),view);
        }
        views=Collections.unmodifiableMap(nodes);
        var declared=new HashSet<>(classIds);declared.addAll(valueIds);
        if(!declared.equals(views.keySet())||classIds.stream().anyMatch(valueIds::contains))
            throw root.location().error("OBSERVER_VIEW_EXECUTION_CATEGORY_UNRESOLVED");
        var classes=new LinkedHashMap<String,String>();
        for(String id:classIds)classes.put(id,requireView(id).attr("name"));
        classViews=Collections.unmodifiableMap(classes);
        var tuples=new LinkedHashMap<String,TupleType>();
        for(var view:views.values()) {
            var fields=view.children("field");
            if(fields.isEmpty()||fields.size()>64)throw view.location().error("OBSERVER_FIELD_CAPACITY_OR_EMPTY");
            var fieldNames=new HashSet<String>();
            for(var field:fields) {
                if(!identifier(field.attr("name"))||!fieldNames.add(field.attr("name"))||field.optional("origin").isBlank())
                    throw field.location().error("OBSERVER_FIELD_IDENTITY_OR_ORIGIN_REQUIRED");
                String type=field.attr("type");
                if(!SCALARS.contains(type)&&(!classIds.contains(view.attr("id"))||!classViews.containsValue(type)))
                    throw field.location().error("OBSERVER_FIELD_TYPE_UNSUPPORTED:"+type);
            }
            if(valueIds.contains(view.attr("id"))) {
                var parts=new TupleType.Part[fields.size()];
                for(int i=0;i<parts.length;i++)parts[i]=new TupleType.Part(i,fields.get(i).attr("name"),scalar(fields.get(i).attr("type")));
                tuples.put(view.attr("id"),TypeFactory.mkTuple(parts));
            }
        }
        valueTypes=Collections.unmodifiableMap(tuples);
    }
    public Map<String,String> classViews(){return classViews;}
    public String referenceAssociation(String viewId,String fieldName) {
        var view=requireView(viewId);var field=view.children("field").stream().filter(f->f.attr("name").equals(fieldName)&&classViews.containsValue(f.attr("type"))).findFirst().orElseThrow();
        return association(view,field);
    }
    public TupleType valueType(String viewId) {
        var type=valueTypes.get(viewId);
        if(type==null)throw root.location().error("OBSERVER_VALUE_VIEW_UNRESOLVED:"+viewId);
        return type;
    }
    public void install(UseModelApi api,Use75ModelBinding binding) throws UseApiException {
        if(api.getModel()!=binding.model())throw new IllegalArgumentException("OBSERVER_SCHEMA_MODEL_MISMATCH");
        var model=binding.model();
        // Validate all collisions before changing the construction candidate.
        var associations=new HashSet<String>();
        for(var entry:classViews.entrySet()) {
            var view=requireView(entry.getKey());
            if(model.getClassifier(entry.getValue())!=null)throw view.location().error("OBSERVER_CLASS_COLLISION:"+entry.getValue());
            for(var field:view.children("field"))if(classViews.containsValue(field.attr("type"))) {
                String association=association(view,field);
                if(!associations.add(association)||model.getAssociation(association)!=null)
                    throw field.location().error("OBSERVER_ASSOCIATION_COLLISION:"+association);
            }
        }
        for(var entry:classViews.entrySet()) {
            var view=requireView(entry.getKey());var cls=api.createClass(entry.getValue(),false);
            cls.addAnnotation(metadata(view,null));
            cls.addAnnotation(new MElementAnnotation(Use75CslModelBackend.HELPER,Map.of(
                    "kind","EXTERNAL_OBSERVER_VIEW","id64",Use75CslModelBackend.encode("observer:"+view.attr("id")))));
        }
        for(var entry:classViews.entrySet()) {
            var view=requireView(entry.getKey());
            for(var field:view.children("field")) {
                if(classViews.containsValue(field.attr("type"))) {
                    String name=association(view,field);
                    api.createAssociation(name,entry.getValue(),"owners_"+safe(view.attr("id"))+"_"+field.attr("name"),"*",0,
                            field.attr("type"),field.attr("name"),"0..1",0);
                    model.getAssociation(name).addAnnotation(metadata(view,field));
                    model.getAssociation(name).addAnnotation(new MElementAnnotation(Use75CslModelBackend.HELPER,Map.of(
                            "kind","EXTERNAL_OBSERVER_REFERENCE","id64",Use75CslModelBackend.encode(view.attr("id")+"::"+field.attr("name")))));
                } else {
                    var attribute=api.createAttributeEx(model.getClass(entry.getValue()),field.attr("name"),scalar(field.attr("type")));
                    attribute.addAnnotation(metadata(view,field));
                }
            }
        }
        model.addAnnotation(new MElementAnnotation(annotation,Map.of("version",version,"mappingHash",sourceHash)));
    }
    private MElementAnnotation metadata(MappingSpecification.Node view,MappingSpecification.Node field) {
        var values=new LinkedHashMap<String,String>();
        values.put("version",version);values.put("mappingHash",sourceHash);
        values.put("view64",Use75CslModelBackend.encode(view.attr("id")));
        values.put("keys64",Use75CslModelBackend.encode(view.attr("keyFields")));
        // Identity keys stay in the runtime binding index; their types are not
        // guessed as native attributes. XML initial/absent clauses are metadata,
        // never default values or proof that a source baseline was observed.
        var node=field==null?view:field;
        values.put("sourceLine",Integer.toString(node.location().line()));
        values.put("details64",Base64.getEncoder().encodeToString(CanonicalJson.encode(node.attributes(),64*1024)));
        if(field!=null)values.put("field64",Use75CslModelBackend.encode(field.attr("name")));
        return new MElementAnnotation(annotation,values);
    }
    private MappingSpecification.Node requireView(String id) {
        var node=views.get(id);if(node==null)throw root.location().error("OBSERVER_VIEW_UNRESOLVED:"+id);return node;
    }
    private static String association(MappingSpecification.Node view,MappingSpecification.Node field){return "Observer_"+safe(view.attr("id"))+"_"+field.attr("name");}
    private static String safe(String id){return id.replace('-','_').replace('.','_');}
    private static boolean identifier(String value){return value.matches("[A-Za-z_][A-Za-z0-9_]*");}
    private static Type scalar(String type) {
        return switch(type) {
            case "Integer"->TypeFactory.mkInteger();case "String"->TypeFactory.mkString();case "Boolean"->TypeFactory.mkBoolean();
            case "Set(String)"->TypeFactory.mkSet(TypeFactory.mkString());
            default->throw new IllegalArgumentException("OBSERVER_SCALAR_TYPE_UNSUPPORTED:"+type);
        };
    }
}
