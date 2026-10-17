package org.tzi.use.plugins.jacamo.semantics.binding;

import java.util.*;
import org.tzi.use.uml.mm.*;
import org.tzi.use.plugins.jacamo.codegrounded.use.*;
import org.tzi.use.plugins.jacamo.semantics.csl.CslUseBinding;

/** Read-only inventory of the already compiled authoritative MModel, for both native and observer profiles. */
public final class ProjectionBindingRegistry implements UseProjectionBinding {
    public record Attribute(String owner,String name,String type,List<String> sourceIds){public Attribute{sourceIds=List.copyOf(sourceIds);}}
    public record Type(String symbol,String semanticKind,String sourceId,List<String> parents,List<Attribute> attributes){public Type{parents=List.copyOf(parents);attributes=List.copyOf(attributes);}}
    public record End(String type,String role,String multiplicity,int aggregation,List<String> qualifiers){public End{qualifiers=List.copyOf(qualifiers);}}
    public record Relation(String symbol,List<End> ends){public Relation{ends=List.copyOf(ends);}}
    private final MModel model;private final String profile;private final List<Type> types;private final List<Relation> relations;
    public ProjectionBindingRegistry(MModel model) {
        this.model=Objects.requireNonNull(model);profile=CslUseBinding.active(model)?CslUseBinding.configuration(model).profile().name():"NATIVE_CURRENT";
        types=model.classes().stream().sorted(Comparator.comparing(MClass::name)).map(c->{String source=DomainProjection.decode(c.getAnnotationValue(DomainProjection.ANNOTATION,"sourceId64"));if(source.isBlank())throw new IllegalArgumentException("PROJECTION_CLASS_SOURCE_BINDING_MISSING:"+c.name());return new Type(c.name(),CslUseBinding.active(model)?c.getAnnotationValue("CslClassifier","kind"):DomainProjection.kind(c),source,c.parents().stream().map(MClassifier::name).sorted().toList(),c.attributes().stream().sorted(Comparator.comparing(MAttribute::name)).map(a->{var origins=new TreeSet<String>();if(a.getAnnotation("CslAttribute")!=null)origins.add(DomainProjection.decode(a.getAnnotationValue("CslAttribute","id64")));for(var annotation:a.getAllAnnotations().entrySet())if(annotation.getKey().startsWith("PropertySource_"))origins.add(DomainProjection.decode(annotation.getValue().getAnnotationValue("sourceId64")));if(origins.isEmpty())origins.add(source);return new Attribute(c.name(),a.name(),a.type().toString(),List.copyOf(origins));}).toList());}).toList();
        relations=model.associations().stream().sorted(Comparator.comparing(MAssociation::name)).map(a->new Relation(a.name(),a.associationEnds().stream().map(e->new End(e.cls().name(),e.nameAsRolename(),e.multiplicity().toString(),e.aggregationKind(),e.getQualifiers().stream().map(q->q.name()+":"+q.type()).toList())).toList())).toList();
    }
    public String profileId(){return profile;}public MModel model(){return model;}public List<Type> types(){return types;}public List<Relation> relations(){return relations;}
    public List<Type> forSource(String source){return types.stream().filter(t->t.sourceId().equals(source)).toList();}
}
