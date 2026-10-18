package org.tzi.use.plugins.jacamo.semantics.runtime;

import java.util.*;
import org.tzi.use.plugins.jacamo.codegrounded.use.DomainProjection;
import org.tzi.use.plugins.jacamo.semantics.csl.*;
import org.tzi.use.plugins.jacamo.semantics.profile.*;
import org.tzi.use.uml.mm.MAttribute;
import org.tzi.use.uml.ocl.value.Value;
import org.tzi.use.uml.sys.MObject;

/** Compatibility adapter retaining the existing local-profile lookup/codec behavior exactly. */
final class LegacyObservationMappingBindings implements ObservationMappingBindings {
    private final CslMonitorConfiguration configuration;private final List<Type> types;
    LegacyObservationMappingBindings(CslMonitorConfiguration configuration) {
        this.configuration=configuration;types=configuration.schema().classifiers().stream().map(c->new Type(c.id(),c.sourceId(),c.symbol(),Kind.valueOf(c.kind().name()),"")).toList();
    }
    public List<Type> types(){return types;}
    public boolean functionalObservers(){return configuration.profile()==SemanticProjectionProfile.CSL_OBSERVER_V2;}
    public boolean hasNesting(){return !configuration.schema().policy().nesting().isEmpty();}
    public boolean nestingRegistered(String child,String parent){var schema=configuration.schema();return schema.policy().nesting().stream().anyMatch(n->schema.classifier(n.childContext()).sourceId().equals(child)&&schema.classifier(n.parentContext()).sourceId().equals(parent));}
    public List<MAttribute> properties(MObject object,String sourceName){return object.cls().allAttributes().stream().filter(a->sourceName.equals(DomainProjection.decode(a.getAnnotationValue("CslAttribute","sourceName64")))).toList();}
    public Value decode(MObject object,MAttribute attribute,List<?> values,List<?> types){return ScalarValueCodec.decode(attribute.type().toString(),values,types);}
}
