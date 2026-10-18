package org.tzi.use.plugins.jacamo.semantics.runtime;

import java.util.*;
import org.tzi.use.plugins.jacamo.semantics.mapping.*;
import org.tzi.use.plugins.jacamo.semantics.csl.ScalarValueCodec;
import org.tzi.use.plugins.jacamo.semantics.evidence.SemanticJson;
import org.tzi.use.semantic.engine.use75.*;
import org.tzi.use.uml.mm.*;
import org.tzi.use.uml.ocl.value.Value;
import org.tzi.use.uml.sys.MObject;

/** Native lookup compiled directly from canonical bindings. No CslSchema/RequirementModel conversion. */
public final class CanonicalObservationMappingBindings implements ObservationMappingBindings {
    private final SemanticProgram program;private final MModel model;private final List<Type> types;
    private final Map<String,SemanticBindingIndex.Binding> entities=new HashMap<>();
    public CanonicalObservationMappingBindings(SemanticProgram program,MModel model) {
        program.assertUnchanged();if(!program.hash().equals(model.getAnnotationValue("CanonicalSemanticProgram","programHash")))throw new IllegalArgumentException("RUNTIME_MAPPING_PROGRAM_MODEL_MISMATCH");
        this.program=program;this.model=model;var compiled=new ArrayList<Type>();
        for(var binding:program.bindings().bindings())if(Set.of(SemanticBindingIndex.Kind.ENTITY,SemanticBindingIndex.Kind.ROLE,SemanticBindingIndex.Kind.ORG_CONTEXT).contains(binding.kind())) {
            var kind=Kind.valueOf(binding.kind().name());String javaClass=kind==Kind.ENTITY?JaCaMoCslMappingRules.javaClass(program.source(),binding):"";
            compiled.add(new Type(binding.targetId(),binding.sourceId(),binding.targetId(),kind,javaClass));
            if(kind==Kind.ENTITY)entities.put(binding.targetId(),binding);
        }
        types=List.copyOf(compiled);
    }
    public List<Type> types(){return types;}
    public boolean canonical(){return true;}
    // These public facts remain journal evidence in V1; old V2 observer mode is unchanged.
    public boolean functionalObservers(){return false;}
    public boolean nestingRegistered(String child,String parent){return false;}
    public boolean hasNesting(){return false;}
    public List<MAttribute> properties(MObject object,String sourceName) {
        var binding=entity(object);
        return object.cls().allAttributes().stream().filter(a->sourceName.equals(binding.metadata().get("property:"+a.name()))).toList();
    }
    public Value decode(MObject object,MAttribute attribute,List<?> values,List<?> javaTypes) {
        if(values.size()!=1||javaTypes.size()!=1||!(values.getFirst() instanceof String text)||!(javaTypes.getFirst() instanceof String actual))throw new IllegalArgumentException("OBS_PROPERTY_TYPED_SCALAR_REQUIRED");
        var enumeration=program.cslModel().enums().stream().filter(e->e.name().equals(attribute.type().toString())).findFirst();
        if(enumeration.isPresent()) {
            if(!actual.equals(entity(object).metadata().get("enumJavaType:"+enumeration.get().name())))throw new IllegalArgumentException("OBS_ENUM_SOURCE_TYPE_MISMATCH:"+actual);
            return new Use75ValueCodec().decode(enumeration.get(),text,model);
        }
        return ScalarValueCodec.decode(attribute.type().toString(),values,javaTypes);
    }
    public void propertyWritten(SemanticObservationMapper.Port port,MObject object,MAttribute attribute,Value before,Value after) {
        String mutable=attribute.getAnnotationValue(Use75CslModelBackend.ATTRIBUTE,"mutable");
        String hash=valueHash(after),first=port.metadata(object,"immutableFirstValueHash:"+attribute.name());
        if("false".equals(mutable)) {
            if(first.isBlank())port.metadata(object,"immutableFirstValueHash:"+attribute.name(),hash);
            else if(!first.equals(hash))port.metadata(object,"immutableChange:"+attribute.name(),"OBSERVED_FACT_CHANGED_IMMUTABLE_ATTRIBUTE");
        }
        port.metadata(object,"observedValueHash:"+attribute.name(),hash);port.metadata(object,"propertyAbsent:"+attribute.name(),"false");
    }
    public static String valueHash(Value value){return SemanticJson.hash(List.of(value.type().toString(),value.toString()));}
    private SemanticBindingIndex.Binding entity(MObject object) {
        var binding=entities.get(object.cls().name());if(binding==null)throw new IllegalArgumentException("RUNTIME_ENTITY_BINDING_UNRESOLVED:"+object.cls().name());return binding;
    }
    public String programHash(){return program.hash();}
}
