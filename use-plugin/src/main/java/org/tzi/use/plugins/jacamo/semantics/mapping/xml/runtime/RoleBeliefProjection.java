package org.tzi.use.plugins.jacamo.semantics.mapping.xml.runtime;

import java.util.*;
import org.jacamo.bridge.contract.Completeness;
import org.tzi.use.api.UseApiException;
import org.tzi.use.plugins.jacamo.semantics.mapping.xml.MappingSpecification;
import org.tzi.use.plugins.jacamo.semantics.runtime.SemanticObservationMapper.Port;
import org.tzi.use.plugins.jacamo.semantics.runtime.CanonicalObservationMappingBindings;
import org.tzi.use.semantic.engine.use75.Use75CslModelBackend;
import org.tzi.use.uml.mm.*;
import org.tzi.use.uml.ocl.value.*;

/** Epistemic attributes of the exact current role player. Does not create roles,
 * alter membership quality, or treat missing mental evidence as world falsity. */
public final class RoleBeliefProjection {
    private record Binding(String id,MClass role,MAttribute attribute,String dataset) { }
    private final String program;
    private final List<Binding> bindings;
    public RoleBeliefProjection(MappingSpecification specification,MModel model,String program) {
        this.program=Objects.requireNonNull(program);var result=new ArrayList<Binding>();
        var sections=specification.root().child("runtime").children("beliefBindings");
        if(sections.size()>1)throw specification.root().location().error("BELIEF_BINDINGS_DUPLICATE");
        if(!sections.isEmpty())for(var node:sections.getFirst().children("bind")) {
            MClass role=model.getClass(node.attr("roleType"));MAttribute attribute=role==null?null:role.attribute(node.attr("attribute"),true);
            var dataset=specification.requireId(node.attr("datasetRef"));
            if(role==null||!role.isSubClassifierOf(model.getClass("RoleOccurrence"))||attribute==null||attribute.isDerived()
                    ||!attribute.type().isTypeOfString()||!"true".equals(attribute.getAnnotationValue(Use75CslModelBackend.ATTRIBUTE,"mutable"))
                    ||!"true".equals(attribute.getAnnotationValue(Use75CslModelBackend.ATTRIBUTE,"optional"))||!dataset.name().equals("beliefViews"))throw node.location().error("BELIEF_BINDING_REQUIRES_OPTIONAL_MUTABLE_ROLE_STRING_VIEW");
            if(result.stream().anyMatch(b->b.attribute().name().equals(attribute.name())&&(role.isSubClassifierOf(b.role())||b.role().isSubClassifierOf(role))))throw node.location().error("BELIEF_TARGET_ATTRIBUTE_AMBIGUOUS");
            result.add(new Binding(node.attr("id"),role,attribute,dataset.attr("id")));
        }
        if(result.size()>256)throw specification.root().location().error("BELIEF_BINDING_CAPACITY");bindings=List.copyOf(result);
    }
    public List<M0RuleExecutor.Outcome> apply(Port port,String epoch,String cut,Map<String,RuntimeTables.Dataset> datasets)throws UseApiException {
        var result=new ArrayList<M0RuleExecutor.Outcome>();
        for(var binding:bindings) {
            var dataset=datasets.get(binding.dataset());if(dataset==null)throw new IllegalArgumentException("BELIEF_DATASET_UNRESOLVED");
            var players=new HashMap<String,RuntimeTables.Row>();
            for(var row:dataset.rows()) {
                if(!row.identity().equals(row.values().get("player"))||!row.identity().equals(row.proofs().get("physical-identity"))||players.putIfAbsent(row.identity(),row)!=null)throw new IllegalArgumentException("BELIEF_PLAYER_IDENTITY_AMBIGUOUS");
            }
            for(var object:port.objects())if(object.cls().isSubClassifierOf(binding.role())&&program.equals(port.metadata(object,"xml:m0:program"))) {
                String player=port.metadata(object,"rolePlayer"),attribute=binding.attribute().name();var row=players.get(player);
                boolean current=epoch.equals(port.metadata(object,"xml:m0:epoch"))&&cut.equals(port.metadata(object,"xml:m0:cut"))&&"COMPLETE".equals(port.metadata(object,"xml:m0:quality"));
                boolean known=current&&row!=null&&row.quality()==Completeness.COMPLETE&&row.values().get("beliefJson") instanceof String;
                Value value=known?new StringValue((String)row.values().get("beliefJson")):UndefinedValue.instance;
                port.value(object,attribute,value);
                if(known)CanonicalObservationMappingBindings.recordPropertyWritten(port,object,binding.attribute(),value);
                else {port.metadata(object,"propertyAbsent:"+attribute,"true");port.metadata(object,"observedValueHash:"+attribute,"");}
                port.metadata(object,"belief:view:cut:"+attribute,cut);port.metadata(object,"belief:view:source:"+attribute,player);
                port.metadata(object,"belief:view:quality:"+attribute,known?"COMPLETE":"INCOMPLETE");
                result.add(new M0RuleExecutor.Outcome(binding.id(),known?"MAPPED":"INCOMPLETE",port.metadata(object,"semanticId"),object,known?"EXACT_PLAYER_STORED_BELIEF_VIEW":"CURRENT_PLAYER_BELIEF_VIEW_UNAVAILABLE"));
            }
        }
        return List.copyOf(result);
    }
}
