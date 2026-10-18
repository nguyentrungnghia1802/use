package org.tzi.use.plugins.jacamo.semantics.mapping;

import java.util.*;
import org.jacamo.bridge.contract.*;
import org.jacamo.bridge.contract.semantic.MoiseSemanticContract.*;
import org.tzi.use.plugins.jacamo.codegrounded.use.*;
import org.tzi.use.plugins.jacamo.semantics.csl.ScalarValueCodec;
import org.tzi.use.semantic.engine.frontend.CslFrontend;
import org.vnu.sme.goal.dsl.csl.mm.*;
import static org.tzi.use.plugins.jacamo.semantics.mapping.SemanticBindingIndex.Kind.*;

/** Selected exact source definitions plus authored domain views, never runtime population. */
public final class JaCaMoCslMappingRules {
    public record Result(CslModel model,List<MappingDiagnostic> diagnostics){public Result{diagnostics=List.copyOf(diagnostics);}}
    public Result map(ModelSnapshot input,NormalizedSourceModel source,MappingRuleSet rules,CslModel authored) {
        var selected=new SemanticBindingIndex(rules.bindings());var diagnostics=new ArrayList<MappingDiagnostic>();
        var roles=new LinkedHashMap<String,RoleSemantic>();var groups=new LinkedHashMap<String,GroupSemantic>();
        for(var org:input.semanticContract().moiseOrganizations()) {
            MoiseDomainProjection.validate(org);org.structuralSpecification().roles().forEach(r->roles.put(r.metadata().semanticId(),r));
            org.structuralSpecification().groups().forEach(g->groups.put(g.metadata().semanticId(),g));
        }
        var roleBindings=new LinkedHashMap<String,SemanticBindingIndex.Binding>();var contextBindings=new LinkedHashMap<String,SemanticBindingIndex.Binding>();
        for(var binding:selected.bindings()) {
            source.require(binding.sourceId());
            if(binding.kind()==ROLE)roleBindings.put(binding.sourceId(),binding);
            if(binding.kind()==ORG_CONTEXT)contextBindings.put(binding.sourceId(),binding);
            if(binding.kind()==ENTITY)validateEntity(input,source,authored,binding,rules,diagnostics);
        }
        var targetRoles=new ArrayList<>(authored.roles());var generalizations=new ArrayList<>(authored.generalizations());
        for(var binding:roleBindings.values()) {
            var role=roles.get(binding.sourceId());if(role==null)reject(binding,"ROLE_DEFINITION_REQUIRED","A deployment/enactment is not an MOISE Role definition");
            if(role.metadata().capabilityStatus()!=CapabilityStatus.COMPLETE)reject(binding,"ROLE_DEFINITION_INCOMPLETE","PARTIAL source definition cannot prove the selected schema");
            if(role.superRoleSemanticIds().size()>1)reject(binding,"MULTIPLE_ROLE_PARENTS_UNSUPPORTED","CSLv4 profile requires one parent");
            if(role.abstractRole()) {
                if(binding.abstractionReason().isBlank())reject(binding,"ABSTRACT_ROLE_CONCRETE_VIEW_REQUIRES_APPROVAL","CSL Roles are concrete; supply an authored abstraction decision");
                diagnostics.add(note(binding,"ABSTRACT_ROLE_CONCRETE_VIEW",binding.abstractionReason()));
            }
            var parents=new ArrayList<String>();
            for(String parent:role.superRoleSemanticIds()) {
                var parentBinding=roleBindings.get(parent);
                if(parentBinding==null)reject(binding,"SELECTED_ROLE_PARENT_BINDING_REQUIRED",parent);
                parents.add(parentBinding.targetId());
            }
            var existing=authored.findRole(binding.targetId());
            if(existing.isPresent()) {if(!existing.get().parentRoles().equals(parents))reject(binding,"SOURCE_TARGET_ROLE_INHERITANCE_MISMATCH",parents.toString());}
            else targetRoles.add(new CslRole(binding.targetId(),parents,List.of()));
            for(var parent:parents){var edge=new CslGeneralization(binding.targetId(),parent);if(!generalizations.contains(edge))generalizations.add(edge);}
        }
        var targetGroups=new ArrayList<CslGroup>();var ownership=new HashMap<String,String>();
        for(var binding:roleBindings.values()) {
            var candidates=contextBindings.values().stream().filter(c->{var group=groups.get(c.sourceId());return group!=null&&group.roleSemanticIds().contains(binding.sourceId());}).toList();
            String explicit=binding.metadata().getOrDefault("contextSourceId","");
            if(!explicit.isBlank()) {
                if(candidates.stream().noneMatch(c->c.sourceId().equals(explicit)))reject(binding,"ROLE_DECLARATION_CONTEXT_MISMATCH",explicit);
                ownership.put(binding.sourceId(),explicit);
            } else if(candidates.size()==1)ownership.put(binding.sourceId(),candidates.getFirst().sourceId());
            else if(candidates.size()>1)reject(binding,"ROLE_DECLARATION_CONTEXT_AMBIGUOUS","Choose contextSourceId; CSL declaration ownership is singular");
        }
        for(var context:contextBindings.values()) {
            var group=groups.get(context.sourceId());if(group==null)reject(context,"GROUP_DEFINITION_REQUIRED","Runtime group deployment is not an MOISE Group definition");
            if(group.metadata().capabilityStatus()!=CapabilityStatus.COMPLETE)reject(context,"GROUP_DEFINITION_INCOMPLETE","PARTIAL source definition");
            var existing=authored.findOrgContext(context.targetId());var members=new LinkedHashMap<String,CslGroupMember>();
            existing.ifPresent(c->c.members().forEach(m->members.put(m.type(),m)));
            for(var role:roleBindings.values())if(context.sourceId().equals(ownership.get(role.sourceId())))members.putIfAbsent(role.targetId(),new CslGroupMember(role.targetId(),CslCardinality.unlimited(0)));
            for(var child:contextBindings.values()) {
                var definition=groups.get(child.sourceId());if(definition!=null&&definition.parentGroupSemanticId().equals(context.sourceId()))members.putIfAbsent(child.targetId(),new CslGroupMember(child.targetId(),CslCardinality.unlimited(0)));
            }
            targetGroups.add(new CslGroup(context.targetId(),Optional.empty(),existing.map(CslGroup::attributes).orElse(List.of()),List.copyOf(members.values()),List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),true));
            diagnostics.add(note(context,"SOURCE_CONTEXTUAL_POLICY_TRACE_ONLY","Declaration containment only; MOISE counts/scoped relations remain in the source complement, not CSL runtime counts"));
        }
        for(var context:authored.groups())if(contextBindings.values().stream().noneMatch(b->b.targetId().equals(context.name())))targetGroups.add(context);
        var model=new CslModel(authored.version(),authored.name(),authored.enums(),authored.entities(),targetRoles,targetGroups,authored.relations(),List.of(),authored.compatibilities(),generalizations,authored.invariants(),authored.namedDataTypes());
        new CslFrontend().validate(model,"<mapped-canonical-csl>").requireModel();return new Result(model,diagnostics);
    }
    private void validateEntity(ModelSnapshot input,NormalizedSourceModel source,CslModel model,SemanticBindingIndex.Binding binding,MappingRuleSet rules,List<MappingDiagnostic> diagnostics) {
        var node=source.require(binding.sourceId());
        if(!node.concept().equals("Artifact")||node.sourceKind().equals("CARTAGO_ARTIFACT")||node.sourceKind().equals("CARTAGO_ARTIFACT_INFO"))reject(binding,"ENTITY_REQUIRES_ARTIFACT_TYPE_VIEW",node.sourceKind());
        var entity=model.findEntity(binding.targetId());if(entity.isEmpty())reject(binding,"AUTHORED_ENTITY_VIEW_REQUIRED",binding.targetId());
        String javaClass=javaClass(source,binding);
        boolean declaredProof=input.semanticContract().artifactDeclarations().stream().filter(d->d.javaClass().equals(javaClass))
                .anyMatch(d->d.metadata().evidence().stream().anyMatch(e->e.authority()==org.jacamo.bridge.contract.semantic.EvidenceAuthority.OFFICIAL_CARTAGO_API
                        &&e.fidelity()==org.jacamo.bridge.contract.semantic.Fidelity.EXACT&&e.capabilityStatus()==CapabilityStatus.COMPLETE
                        &&e.sourceJavaFqcn().equals(javaClass)&&e.sourceSemanticId().equals(d.metadata().semanticId())
                        &&e.sourceDigest().matches("[0-9a-f]{64}")&&(e.sourceUri().startsWith("file:")||e.sourceUri().startsWith("jar:file:"))
                        &&e.diagnostics().contains("DECLARED_CARTAGO_ARTIFACT_APPLICATION_CLASS")));
        boolean official=declaredProof||input.semanticContract().cartagoEnvironments().stream().flatMap(e->e.artifactTypes().stream()).anyMatch(t->t.javaClassName().equals(javaClass)&&t.metadata().capabilityStatus()==CapabilityStatus.COMPLETE&&t.metadata().sourceKind().equals("CARTAGO_ARTIFACT_TYPE"));
        if(!official&&!DomainProjection.domainArtifact(javaClass)) {
            if(Boolean.parseBoolean(rules.options().getOrDefault("requireArtifactProofAtImport","false")))reject(binding,"ARTIFACT_TYPE_PROOF_REQUIRED",javaClass);
            diagnostics.add(note(binding,"ARTIFACT_TYPE_PROOF_PENDING_RUNTIME","Exact declared Java name retained; no live object may be created without authoritative runtime type proof"));
        }
        for(var attribute:attributes(model,entity.orElseThrow(),new HashSet<>())) {
            String property=binding.metadata().getOrDefault("property:"+attribute.name(),"");
            if(property.isBlank())reject(binding,"EXACT_PROPERTY_BINDING_REQUIRED",attribute.name());
            diagnostics.add(note(binding,"PROPERTY_BINDING_REQUIRES_ACCEPTED_RUNTIME_CUT",attribute.name()+" maps exactly to property "+property+"; authored type is not evidence of a current value"));
            if(attribute.type() instanceof CslNamedDataType)reject(binding,"EXACT_RUNTIME_CODEC_REQUIRED",attribute.type().sourceName());
            String javaType=binding.metadata().getOrDefault("javaType:"+attribute.name(),"");
            if(javaType.isBlank()) {diagnostics.add(note(binding,"PROPERTY_TYPE_AUTHORED_PENDING_OBSERVATION",attribute.name()+":"+attribute.type().sourceName()));continue;}
            try {
                if(attribute.type() instanceof CslEnum enumeration) {
                    if(!javaType.equals(binding.metadata().get("enumJavaType:"+enumeration.name())))reject(binding,"ENUM_JAVA_TYPE_BINDING_REQUIRED",enumeration.name());
                } else {
                    String sample=switch(attribute.type().sourceName()){case "Boolean"->"true";case "String"->"sample";default->"0";};
                    ScalarValueCodec.decode(attribute.type().sourceName(),List.of(sample),List.of(javaType));
                }
            }catch(IllegalArgumentException unsupported){reject(binding,"PROPERTY_SOURCE_TARGET_TYPE_MISMATCH",attribute.name()+":"+unsupported.getMessage());}
        }
    }
    public static String javaClass(NormalizedSourceModel source,SemanticBindingIndex.Binding binding) {
        var node=source.require(binding.sourceId());var field=node.features().getOrDefault("javaClass",node.features().get("javaClassName"));
        if(field==null||!(field.value() instanceof String name)||name.isBlank()) {reject(binding,"EXACT_ARTIFACT_JAVA_CLASS_REQUIRED",binding.sourceId());return "";}
        return (String)field.value();
    }
    private List<CslAttribute> attributes(CslModel model,CslEntity entity,Set<String> seen) {
        if(!seen.add(entity.name())||seen.size()>256)throw new IllegalArgumentException("ENTITY_INHERITANCE_CYCLE_OR_DEPTH_LIMIT");
        var values=new ArrayList<CslAttribute>();entity.specializes().ifPresent(p->values.addAll(attributes(model,model.findEntity(p).orElseThrow(),seen)));values.addAll(entity.attributes());return values;
    }
    private static MappingDiagnostic note(SemanticBindingIndex.Binding binding,String code,String reason){return new MappingDiagnostic(MappingDiagnostic.Kind.TRACE_ONLY,code,binding.sourceId(),binding.targetId(),binding.ruleId(),"AUTHORED_MAPPING_AND_OFFICIAL_SOURCE",0,reason);}
    private static void reject(SemanticBindingIndex.Binding binding,String code,String reason){throw new MappingAdmissionException(List.of(new MappingDiagnostic(MappingDiagnostic.Kind.UNSUPPORTED,code,binding.sourceId(),binding.targetId(),binding.ruleId(),"OFFICIAL_SOURCE_OR_AUTHORED_VIEW",0,reason)));}
}
