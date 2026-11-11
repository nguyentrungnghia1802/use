package org.tzi.use.plugins.jacamo.semantics.mapping.compact;

import java.nio.charset.StandardCharsets;
import java.util.*;
import org.jacamo.bridge.contract.CanonicalJson;
import org.tzi.use.plugins.jacamo.semantics.evidence.SemanticJson;
import org.tzi.use.plugins.jacamo.semantics.mapping.SemanticProgram;
import org.tzi.use.plugins.jacamo.semantics.mapping.xml.runtime.InvariantActivation;
import org.tzi.use.plugins.jacamo.semantics.runtime.SemanticObservationMapper.Port;
import org.tzi.use.uml.mm.*;

/** Explicit operational activation from a complete public role roster.
 * The native invariant body remains unchanged; activation is latched per incarnation. */
public final class CompactInvariantActivation {
    private record Binding(MClassInvariant invariant,String context,String roleSource) { }
    private final SemanticProgram program;
    private final List<Binding> bindings;
    public CompactInvariantActivation(SemanticProgram program,MModel model,boolean declare) {
        this.program=program;var result=new ArrayList<Binding>();var seen=new HashSet<String>();
        for(var map:program.compactPlan().specification().cslMappings()) {
            String target=map.attributes().getOrDefault("activateInvariant","");
            if(target.isBlank()) {
                if(map.attributes().containsKey("activationRole")||map.attributes().containsKey("invariantActivationPolicy"))throw map.location().error("COMPACT_ACTIVATION_TARGET_REQUIRED");
                continue;
            }
            if(!(SourceReference.parse(map.source()) instanceof SourceReference.MoiseGroupRef)||!InvariantActivation.POLICY.equals(map.attributes().get("invariantActivationPolicy"))||!seen.add(target))throw map.location().error("COMPACT_ACTIVATION_POLICY_OR_SOURCE_INVALID");
            var invariant=model.classInvariants().stream().filter(i->i.qualifiedName().equals(target)).findFirst().orElseThrow(()->map.location().error("COMPACT_ACTIVATION_INVARIANT_UNRESOLVED"));
            if(!invariant.cls().name().equals(map.target()))throw map.location().error("COMPACT_ACTIVATION_CONTEXT_MISMATCH");
            String role=map.attributes().getOrDefault("activationRole","");
            var roleBinding=program.compactPlan().cslBindings().stream().filter(b->b.targetRef().raw().equals(role)&&b.sourceRef() instanceof SourceReference.MoiseRoleRef).toList();
            if(roleBinding.size()!=1)throw map.location().error("COMPACT_ACTIVATION_EXACT_ROLE_REQUIRED");
            String roleSource=program.bindings().find(org.tzi.use.plugins.jacamo.semantics.mapping.SemanticBindingIndex.Kind.ROLE,role).orElseThrow().sourceId();
            if(declare)invariant.addAnnotation(new MElementAnnotation(InvariantActivation.ANNOTATION,Map.of("program",program.hash(),"policy",InvariantActivation.POLICY,"id",target,"source",roleSource,"epochKey","compact:epoch")));
            else if(!program.hash().equals(invariant.getAnnotationValue(InvariantActivation.ANNOTATION,"program")))throw map.location().error("COMPACT_ACTIVATION_DECLARATION_REQUIRED");
            result.add(new Binding(invariant,map.target(),roleSource));
        }
        bindings=List.copyOf(result);
    }
    public void apply(Port port,String epoch,String cut) {
        for(var binding:bindings)for(var context:port.objects()) {
            if(!context.cls().name().equals(binding.context)||!epoch.equals(port.metadata(context,"compact:epoch"))||!program.hash().equals(port.metadata(context,"compact:program"))
                    ||!cut.equals(port.metadata(context,"compact:cut"))||!"COMPLETE".equals(port.metadata(context,"compact:quality"))||!"COMPLETE_OBSERVED_BOARD".equals(port.metadata(context,"rolePopulationQuality")))continue;
            if(!port.metadata(context,InvariantActivation.key(binding.invariant)).isBlank())continue;
            boolean active=port.objects().stream().anyMatch(role->binding.roleSource.equals(port.metadata(role,"roleSourceId"))&&port.find(port.metadata(role,"roleContext"))==context&&"COMPLETE".equals(port.metadata(role,"compact:quality"))&&cut.equals(port.metadata(role,"compact:cut")));
            if(!active)continue;
            var proof=new TreeMap<String,Object>();proof.put("policy",InvariantActivation.POLICY);proof.put("program",program.hash());proof.put("epoch",epoch);proof.put("context",context.name());proof.put("cut",cut);proof.put("sourceRole",binding.roleSource);proof.put("hash",SemanticJson.hash(proof));
            port.metadata(context,InvariantActivation.key(binding.invariant),new String(CanonicalJson.encode(proof),StandardCharsets.UTF_8));
        }
    }
}
