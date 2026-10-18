package org.tzi.use.plugins.jacamo.semantics;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.jacamo.bridge.adapter.*;
import org.jacamo.bridge.contract.*;
import org.tzi.use.main.Session;
import org.tzi.use.plugins.jacamo.codegrounded.*;
import org.tzi.use.plugins.jacamo.codegrounded.use.*;
import org.tzi.use.plugins.jacamo.semantics.mapping.*;
import org.tzi.use.plugins.jacamo.semantics.istar.RequirementModel;
import org.tzi.use.semantic.engine.frontend.CslFrontend;
import static org.tzi.use.plugins.jacamo.semantics.mapping.SemanticBindingIndex.Kind.*;
import static org.junit.jupiter.api.Assertions.*;

class CanonicalCslMappingTest {
    private static ModelSnapshot source;
    private static final JaCaMoMappingProfile PROFILE=JaCaMoMappingProfile.observedV1();
    @BeforeAll static void load() throws Exception {
        var path=Path.of("src/test/resources/semantics/profile-v1/monitor.jcm").toAbsolutePath();source=new OfficialProjectAdapter().adapt(new OfficialProjectLoader().load(path),path);
    }
    static MappingRuleSet rules(ModelSnapshot input,String csl) {
        var candidates=new ArrayList<MoiseUseSymbols.Candidate>();
        input.semanticContract().moiseOrganizations().forEach(o->{o.structuralSpecification().roles().forEach(r->candidates.add(new MoiseUseSymbols.Candidate(r.metadata().semanticId(),"Role","R_"+r.roleId())));o.structuralSpecification().groups().forEach(g->candidates.add(new MoiseUseSymbols.Candidate(g.metadata().semanticId(),"Context","C_"+g.groupId())));});
        var symbols=MoiseUseSymbols.allocate(candidates,Set.of("Counter"));var bindings=new ArrayList<SemanticBindingIndex.Binding>();
        input.semanticContract().moiseOrganizations().forEach(o->{
            o.structuralSpecification().roles().forEach(r->bindings.add(new SemanticBindingIndex.Binding(r.metadata().semanticId(),ROLE,symbols.get(r.metadata().semanticId()),symbols.get(r.metadata().semanticId()),"M03",r.abstractRole()?"EXPLICIT_ABSTRACT_MOISE_ROLE_AS_CONCRETE_DECLARATION_ONLY_VIEW":"",Map.of())));
            o.structuralSpecification().groups().forEach(g->bindings.add(new SemanticBindingIndex.Binding(g.metadata().semanticId(),ORG_CONTEXT,symbols.get(g.metadata().semanticId()),symbols.get(g.metadata().semanticId()),"M02","DECLARATION_ONLY",Map.of())));
        });
        bindings.add(new SemanticBindingIndex.Binding(DomainProjection.artifactId(input.semanticContract().artifactDeclarations().getFirst().javaClass()),ENTITY,"Counter","Counter","M18","AUTHORED_COUNTER_VIEW",Map.of("property:p","p","javaType:p","java.lang.Integer")));
        return new MappingRuleSet("2.0","mapped-counter",PROFILE.id(),PROFILE.version(),"",csl,"istar Empty { }",bindings,List.of(),List.of(),Map.of("requireArtifactProofAtImport","true"));
    }
    static String csl(){return "csl v4.0 CounterDomain { entity Counter { p : Integer mutable default 99; } context Counter inv Nonnegative: self.p >= 0; }";}
    @Test void producerClassProofWorksWithoutFrameworkInConsumerLoaderAndMalformedProofRejects() throws Exception {
        assertTrue(source.semanticContract().artifactDeclarations().getFirst().metadata().evidence().stream().anyMatch(e->e.diagnostics().contains("DECLARED_CARTAGO_ARTIFACT_APPLICATION_CLASS")));
        var original=Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(new ClassLoader(null){});
            assertEquals(1,new JaCaMoStaticMappingAdapter().map(source,PROFILE,rules(source,csl())).cslModel().entities().size());
            Map<String,Object> tree=new com.fasterxml.jackson.databind.ObjectMapper().readValue(CanonicalJson.encode(ContractPayloads.model(source)),new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>(){});
            var contract=CanonicalJson.object(tree.get("semanticContract"));
            var declarations=(List<?>)contract.get("artifactDeclarations");
            var metadata=CanonicalJson.object(CanonicalJson.object(declarations.getFirst()).get("metadata"));
            for(Object item:(List<?>)metadata.get("evidence")) {
                var proof=CanonicalJson.object(item);
                if(((List<?>)proof.get("diagnostics")).contains("DECLARED_CARTAGO_ARTIFACT_APPLICATION_CLASS"))proof.put("sourceDigest","not-a-bytecode-hash");
            }
            var malformed=ContractPayloads.model(tree);
            assertTrue(assertThrows(MappingAdmissionException.class,()->new JaCaMoStaticMappingAdapter().map(malformed,PROFILE,rules(malformed,csl()))).getMessage().contains("ARTIFACT_TYPE_PROOF_REQUIRED"));
        } finally {Thread.currentThread().setContextClassLoader(original);}
    }
    @Test void sourceGroupsRolesAndRegisteredArtifactBecomeRealCslM1AndOneEmptyHostSystem() throws Exception {
        var program=new JaCaMoStaticMappingAdapter().map(source,PROFILE,rules(source,csl()));var csl=program.cslModel();
        assertTrue(new CslFrontend().validate(csl,"test").ok());assertEquals(1,csl.entities().size());
        assertEquals("Integer",csl.entities().getFirst().attributes().getFirst().type().sourceName());
        int roleCount=source.semanticContract().moiseOrganizations().stream().mapToInt(o->o.structuralSpecification().roles().size()).sum();assertEquals(roleCount,csl.roles().size());
        assertTrue(csl.orgContexts().stream().flatMap(g->g.members().stream()).anyMatch(m->csl.findRole(m.type()).isPresent()));
        var built=new CodeGroundedNativePipeline().buildCanonicalProgram(source,NativeProjectionMode.AUTO,program);var nativeResult=built.nativeResult();
        assertSame(built.binding().model(),nativeResult.state().system().model());assertSame(nativeResult.model().model(),nativeResult.state().system().model());
        assertTrue(nativeResult.state().system().state().allObjects().isEmpty());assertTrue(nativeResult.state().semanticObjectIndex().isEmpty());
        assertEquals(2,nativeResult.model().model().classInvariants().size(),"authored invariant plus neutral compatibility policy");
        assertEquals("Integer",nativeResult.model().model().getClass("Counter").attribute("p",true).type().toString());
        assertEquals(nativeResult.export().originalStructuralHash(),nativeResult.export().recompiledStructuralHash());
        assertEquals(program.hash(),nativeResult.model().model().getAnnotationValue("CanonicalSemanticProgram","programHash"));
        for(var binding:program.bindings().bindings())if(Set.of(ENTITY,ROLE,ORG_CONTEXT).contains(binding.kind()))assertEquals(1,nativeResult.binding().inventory().forSource(binding.sourceId()).size());
        var session=new Session();new NativeUseSessionActivator().activate(session,nativeResult);assertSame(nativeResult.state().system(),session.system());
        var evidence=Path.of("target/semantic-migration/canonical-static.use");Files.createDirectories(evidence.getParent());Files.writeString(evidence,nativeResult.export().useText());
    }
    @Test void originalRoleInheritanceAndDeclarationOwnersMatchSelectedSourceDefinitions() {
        var program=new JaCaMoStaticMappingAdapter().map(source,PROFILE,rules(source,csl()));
        for(var organization:source.semanticContract().moiseOrganizations())for(var role:organization.structuralSpecification().roles()) {
            var target=program.bindings().source(role.metadata().semanticId()).stream().filter(b->b.kind()==ROLE).findFirst().orElseThrow();
            var actual=program.cslModel().findRole(target.targetId()).orElseThrow();
            var expected=role.superRoleSemanticIds().stream().map(id->program.bindings().source(id).stream().filter(b->b.kind()==ROLE).findFirst().orElseThrow().targetId()).toList();assertEquals(expected,actual.parentRoles());
            if(role.abstractRole())assertTrue(program.diagnostics().stream().anyMatch(d->d.sourceId().equals(role.metadata().semanticId())&&d.code().equals("ABSTRACT_ROLE_CONCRETE_VIEW")));
        }
        assertTrue(program.diagnostics().stream().anyMatch(d->d.code().equals("SOURCE_CONTEXTUAL_POLICY_TRACE_ONLY")));
        assertTrue(program.cslModel().orgContexts().stream().flatMap(g->g.members().stream()).allMatch(m->m.multiplicity().min()==0&&m.multiplicity().max().isEmpty()));
    }
    @Test void abstractRoleMissingParentAndOpaqueOrMismatchedPropertyTypesRejectBeforeHostActivation() {
        var base=rules(source,csl());var abstractBinding=base.bindings().stream().filter(b->b.kind()==ROLE&&!b.abstractionReason().isBlank()).findFirst().orElseThrow();
        var noApproval=new ArrayList<>(base.bindings());noApproval.replaceAll(b->b==abstractBinding?new SemanticBindingIndex.Binding(b.sourceId(),b.kind(),b.targetId(),b.nativeSymbol(),b.ruleId(),"",b.metadata()):b);
        assertUnsupported(base,noApproval,csl(),"ABSTRACT_ROLE_CONCRETE_VIEW_REQUIRES_APPROVAL");
        var noParent=base.bindings().stream().filter(b->!b.sourceId().equals(abstractBinding.sourceId())).toList();assertUnsupported(base,noParent,csl(),"SELECTED_ROLE_PARENT_BINDING_REQUIRED");
        var wrongType=new ArrayList<>(base.bindings());wrongType.replaceAll(b->b.kind()==ENTITY?new SemanticBindingIndex.Binding(b.sourceId(),b.kind(),b.targetId(),b.nativeSymbol(),b.ruleId(),b.abstractionReason(),Map.of("property:p","p","javaType:p","java.lang.Double")):b);
        assertUnsupported(base,wrongType,csl(),"PROPERTY_SOURCE_TARGET_TYPE_MISMATCH");
        assertUnsupported(base,base.bindings(),"csl v4.0 Opaque { datatype Money; entity Counter { p : Money; } }","EXACT_RUNTIME_CODEC_REQUIRED");
    }
    @Test void incompleteSourceAndDuplicateTargetSymbolsCannotBecomeCompleteSchemaClaims() throws Exception {
        var tree=new com.fasterxml.jackson.databind.ObjectMapper().readValue(CanonicalJson.encode(ContractPayloads.model(source)),Map.class);
        assertTrue(changeRoleCapability(tree));var partial=ContractPayloads.model(tree);
        var error=assertThrows(MappingAdmissionException.class,()->new JaCaMoStaticMappingAdapter().map(partial,PROFILE,rules(partial,csl())));
        assertTrue(error.diagnostics().stream().anyMatch(d->d.code().equals("ROLE_DEFINITION_INCOMPLETE")));
        var base=rules(source,csl());var duplicates=new ArrayList<>(base.bindings());var entity=base.bindings().stream().filter(b->b.kind()==ENTITY).findFirst().orElseThrow();duplicates.add(entity);
        assertThrows(IllegalArgumentException.class,()->new SemanticBindingIndex(duplicates));
    }
    @Test void legacyStructuralIntersectionAndFullSourcePreservationStillHold() {
        var program=new JaCaMoStaticMappingAdapter().map(source,PROFILE,rules(source,csl()));
        var old=new OrganizationalMappingRules().derive(source,List.of(),List.of(),List.of(),List.of(),new RequirementModel(List.of()));
        for(var type:old.schema().classifiers())if(type.kind()!=org.tzi.use.plugins.jacamo.semantics.csl.CslSchema.Kind.ENTITY) {
            var mapped=program.bindings().source(type.sourceId());assertEquals(1,mapped.size());
            assertTrue(type.kind()==org.tzi.use.plugins.jacamo.semantics.csl.CslSchema.Kind.ROLE?program.cslModel().findRole(mapped.getFirst().targetId()).isPresent():program.cslModel().findOrgContext(mapped.getFirst().targetId()).isPresent());
        }
        assertEquals(program.source().nodes().stream().mapToInt(n->n.features().size()).sum(),program.complement().decisions().size());
        assertTrue(program.cslModel().entities().stream().noneMatch(e->e.name().equals("AtomicAgent")));
    }
    @Test void explicitlyRegisteredEnumViewKeepsEnumTypeAndRequiresItsExactSourceTypeBinding() {
        String text="csl v4.0 EnumView { enum Color { RED, BLUE } entity Counter { p : Color; } }";var base=rules(source,text);
        var bindings=new ArrayList<>(base.bindings());bindings.replaceAll(b->b.kind()==ENTITY?new SemanticBindingIndex.Binding(b.sourceId(),b.kind(),b.targetId(),b.nativeSymbol(),b.ruleId(),b.abstractionReason(),
                Map.of("property:p","color","javaType:p","domain.Color","enumJavaType:Color","domain.Color")):b);
        var rules=new MappingRuleSet(base.version(),base.id(),base.profileId(),base.profileVersion(),"",text,base.iStarSource(),bindings,List.of(),List.of(),base.options());
        var program=new JaCaMoStaticMappingAdapter().map(source,PROFILE,rules);var built=new CodeGroundedNativePipeline().buildCanonicalProgram(source,NativeProjectionMode.AUTO,program);
        assertEquals("Color",built.binding().classifier("Counter").attribute("p",true).type().toString());assertTrue(built.nativeResult().state().system().state().allObjects().isEmpty());
        bindings.replaceAll(b->b.kind()==ENTITY?new SemanticBindingIndex.Binding(b.sourceId(),b.kind(),b.targetId(),b.nativeSymbol(),b.ruleId(),b.abstractionReason(),Map.of("property:p","color","javaType:p","domain.Color")):b);
        assertUnsupported(base,bindings,text,"PROPERTY_SOURCE_TARGET_TYPE_MISMATCH");
    }
    private void assertUnsupported(MappingRuleSet base,List<SemanticBindingIndex.Binding> bindings,String text,String code) {
        var rules=new MappingRuleSet(base.version(),base.id(),base.profileId(),base.profileVersion(),base.expectedSourceHash(),text,base.iStarSource(),bindings,base.contracts(),base.decisions(),base.options());
        var error=assertThrows(MappingAdmissionException.class,()->new JaCaMoStaticMappingAdapter().map(source,PROFILE,rules));assertTrue(error.diagnostics().stream().anyMatch(d->d.code().equals(code)),error.toString());
    }
    @SuppressWarnings("unchecked") private boolean changeRoleCapability(Object value) {
        if(value instanceof Map<?,?> raw) {
            var map=(Map<String,Object>)raw;if(map.get("metadata") instanceof Map<?,?> metadata&&"MOISE_ROLE".equals(metadata.get("sourceKind"))) {((Map<String,Object>)metadata).put("capabilityStatus","PARTIAL");return true;}
            for(var child:map.values())if(changeRoleCapability(child))return true;
        }else if(value instanceof List<?> list)for(var child:list)if(changeRoleCapability(child))return true;
        return false;
    }
}
