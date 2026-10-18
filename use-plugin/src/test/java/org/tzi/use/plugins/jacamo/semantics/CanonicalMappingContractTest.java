package org.tzi.use.plugins.jacamo.semantics;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.jacamo.bridge.adapter.*;
import org.jacamo.bridge.contract.*;
import org.tzi.use.plugins.jacamo.codegrounded.use.DomainProjection;
import org.tzi.use.plugins.jacamo.semantics.mapping.*;
import org.tzi.use.plugins.jacamo.semantics.evidence.*;
import org.tzi.use.plugins.jacamo.semantics.istar.RequirementModel;
import org.tzi.use.semantic.engine.api.*;
import org.vnu.sme.goal.dsl.csl.mm.CslModel;
import org.vnu.sme.goal.dsl.istar.mm.*;
import static org.junit.jupiter.api.Assertions.*;

class CanonicalMappingContractTest {
    private static ModelSnapshot source;private static NormalizedSourceModel normalized;private static String artifact;
    @BeforeAll static void load() throws Exception {
        var path=Path.of("src/test/resources/semantics/profile-v1/monitor.jcm").toAbsolutePath();
        source=new OfficialProjectAdapter().adapt(new OfficialProjectLoader().load(path),path);normalized=new SourceModelNormalizer().normalize(source);
        artifact=DomainProjection.artifactId(CslCounterArtifact.class.getName());assertNotNull(normalized.require(artifact));
    }
    private SemanticBindingIndex.Binding binding(String name) {return new SemanticBindingIndex.Binding(artifact,SemanticBindingIndex.Kind.ENTITY,name,name,"M18","EXPLICIT_ARTIFACT_DOMAIN_VIEW",Map.of("property:p","p"));}
    private MappingRuleSet rules(String name,JaCaMoMappingProfile profile) {
        String csl="csl v4.0 Domain { entity "+name+" { p : Integer; } }";
        var bindings=new ArrayList<>(CanonicalCslMappingTest.rules(source,csl).bindings());bindings.replaceAll(b->b.kind()==SemanticBindingIndex.Kind.ENTITY?binding(name):b);
        var ss=source.semanticContract().moiseOrganizations().getFirst().structuralSpecification();
        String role=ss.roles().stream().filter(r->r.roleId().equals("worker")).findFirst().orElseThrow().metadata().semanticId();String context=ss.groups().getFirst().metadata().semanticId();
        bindings.add(new SemanticBindingIndex.Binding(role,SemanticBindingIndex.Kind.ACTOR,"Worker","","M03","AUTHORED_INTENTIONAL_ROLE",Map.of("contextSourceId",context,"activationPolicy","ENACTMENT_INTERVAL","episodePolicy","ENACTMENT_INCARNATION")));
        bindings.add(new SemanticBindingIndex.Binding(artifact,SemanticBindingIndex.Kind.GOAL,"positive","","M18","AUTHORED_WORLD_CONDITION",Map.of("selfDeclaration",name,"selfSourceId",source.semanticContract().artifactDeclarations().getFirst().metadata().semanticId())));
        return new MappingRuleSet("2.0","counter-view",profile.id(),profile.version(),normalized.sourceHash(),
                csl,"istar Requirements { role Worker { goal positive : Achieve condition {[ true ]} } }",bindings,List.of(),List.of(),Map.of());
    }
    @Test void snapshotAndAuthoredRulesProduceCanonicalOriginalModelsAndCompleteTrace() {
        var profile=JaCaMoMappingProfile.observedV1();var rules=rules("Counter",profile);var adapter=new JaCaMoStaticMappingAdapter();
        var program=adapter.map(source,profile,rules);assertInstanceOf(CslModel.class,program.cslModel());assertInstanceOf(GoalModel.class,program.goalModel());
        assertTrue(GoalModelValidator.validate(program.goalModel()).isEmpty());assertEquals("Counter",program.cslModel().entities().getFirst().name());
        assertEquals(normalized.sourceHash(),program.source().sourceHash());assertEquals(program.hash(),adapter.map(source,profile,rules).hash());
        assertEquals(normalized.nodes().stream().mapToInt(n->n.features().size()).sum(),program.complement().decisions().size());
        assertArrayEquals(CanonicalJson.encode(ContractPayloads.model(source)),program.complement().assets().getFirst().bytes());
        assertThrows(UnsupportedOperationException.class,()->program.bindings().bindings().add(binding("Other")));
        assertThrows(IllegalArgumentException.class,()->program.requireSourceHash("wrong"));
    }
    @Test void profileAndRuleReplacementChangesProgramWithoutChangingOriginalEngine() throws Exception {
        var first=JaCaMoMappingProfile.observedV1();var second=new JaCaMoMappingProfile("JACAMO_MAPPING_V2_TEST","2.0",first.engine(),first.capabilities());
        var one=new JaCaMoStaticMappingAdapter().map(source,first,rules("Counter",first));var two=new JaCaMoStaticMappingAdapter().map(source,second,rules("Meter",second));
        assertNotEquals(one.hash(),two.hash());assertNotEquals(one.bindings().hash(),two.bindings().hash());assertNotEquals(one.cslHash(),two.cslHash());assertEquals(one.goalHash(),two.goalHash());
        assertSame(one.goalModel().getClass(),two.goalModel().getClass());assertEquals(first.engine().upstreamCommit(),second.engine().upstreamCommit());
        assertNotNull(two.cslModel().findEntity("Meter").orElseThrow());
        Path evidence=Path.of("target/semantic-migration/profile-replacement.json");Files.createDirectories(evidence.getParent());
        Files.write(evidence,CanonicalJson.encode(Map.of("sourceHash",normalized.sourceHash(),"v1Program",one.hash(),"v2Program",two.hash(),"engineCommit",first.engine().upstreamCommit())));
    }
    @Test void versionedRulesDiagnosticAndSourceDecisionsRoundTripWithoutLegacyIr() {
        var profile=JaCaMoMappingProfile.observedV1();var rules=rules("Counter",profile);assertEquals(rules,MappingRuleSet.parse(rules.bytes()));
        var node=normalized.require(artifact);var feature=node.features().entrySet().iterator().next();
        var optional=new UnsupportedMappingDecision(artifact,feature.getKey(),"M18",false,"SOURCE_ONLY_FEATURE",feature.getValue().value(),feature.getValue().origin());
        var withDecision=new MappingRuleSet("2.0",rules.id(),rules.profileId(),rules.profileVersion(),rules.expectedSourceHash(),rules.cslSource(),rules.iStarSource(),rules.bindings(),rules.contracts(),List.of(optional),rules.options());
        var program=new JaCaMoStaticMappingAdapter().map(source,profile,MappingRuleSet.parse(withDecision.bytes()));
        assertEquals(MappingDiagnostic.Kind.UNSUPPORTED,program.diagnostics().getFirst().kind());assertEquals("SOURCE_ONLY_FEATURE",program.diagnostics().getFirst().reason());
        assertEquals(SemanticJson.hash(SemanticJson.tree(optional.diagnostic())),SemanticJson.hash(SemanticJson.tree(program.diagnostics().getFirst())));
        var mandatory=new UnsupportedMappingDecision(optional.sourceId(),optional.feature(),optional.ruleId(),true,optional.reason(),optional.preservedValue(),optional.origin());
        var blocked=new MappingRuleSet("2.0",rules.id(),rules.profileId(),rules.profileVersion(),rules.expectedSourceHash(),rules.cslSource(),rules.iStarSource(),rules.bindings(),rules.contracts(),List.of(mandatory),rules.options());
        var unsupported=assertThrows(MappingAdmissionException.class,()->new JaCaMoStaticMappingAdapter().map(source,profile,blocked));
        assertEquals(MappingDiagnostic.Kind.UNSUPPORTED,unsupported.diagnostics().getFirst().kind());
    }
    @Test void ambiguousBindingsWrongSourceProfileAndAgentEntityCoercionAreRejected() {
        var profile=JaCaMoMappingProfile.observedV1();var rules=rules("Counter",profile);
        assertThrows(IllegalArgumentException.class,()->new SemanticBindingIndex(List.of(binding("Counter"),binding("Counter"))));
        var wrongHash=new MappingRuleSet("2.0",rules.id(),rules.profileId(),rules.profileVersion(),"0".repeat(64),rules.cslSource(),rules.iStarSource(),rules.bindings(),List.of(),List.of(),Map.of());
        assertThrows(IllegalArgumentException.class,()->new JaCaMoStaticMappingAdapter().map(source,profile,wrongHash));
        var other=new JaCaMoMappingProfile("OTHER","1.0",profile.engine(),profile.capabilities());assertThrows(IllegalArgumentException.class,()->new JaCaMoStaticMappingAdapter().map(source,other,rules));
        var agent=normalized.nodes().stream().filter(n->n.concept().equals("Agent")).findFirst().orElseThrow();
        var coercion=new SemanticBindingIndex.Binding(agent.id(),SemanticBindingIndex.Kind.ENTITY,"Counter","Counter","M10","even a supplied reason cannot turn an Agent into world Entity",Map.of());
        var invalid=new MappingRuleSet("2.0",rules.id(),rules.profileId(),rules.profileVersion(),rules.expectedSourceHash(),rules.cslSource(),rules.iStarSource(),List.of(coercion),List.of(),List.of(),Map.of());
        assertThrows(IllegalArgumentException.class,()->new JaCaMoStaticMappingAdapter().map(source,profile,invalid));
    }
    @Test void originalMutableGoalDefinitionCannotChangeAfterProgramOwnership() {
        var profile=JaCaMoMappingProfile.observedV1();var program=new JaCaMoStaticMappingAdapter().map(source,profile,rules("Counter",profile));
        ((Goal)program.goalModel().findElement("positive").orElseThrow()).setCondition(new IStarOclConstraint("false"));
        assertThrows(IllegalStateException.class,program::goalModel);assertThrows(IllegalStateException.class,program::assertUnchanged);
    }
    @Test void completeSourceFeaturesAndLegacyBundleCompatibilityRemainUnchanged() {
        var profile=JaCaMoMappingProfile.observedV1();var canonical=new JaCaMoStaticMappingAdapter().map(source,profile,rules("Counter",profile));
        var config=new OrganizationalMappingRules().derive(source,List.of(),List.of(),List.of(),List.of(),new RequirementModel(List.of()));
        var legacy=new SemanticTransformationService().fromOfficial(source,config);assertEquals(legacy,new MappingBundleCodec().decode(new MappingBundleCodec().encode(legacy)));
        var old=new TreeMap<String,String>();var current=new TreeMap<String,String>();
        legacy.complement().decisions().forEach(d->old.put(d.sourceId()+"/"+d.feature(),SemanticJson.hash(d.preservedValue())+d.origin()));
        canonical.complement().decisions().forEach(d->current.put(d.sourceId()+"/"+d.feature(),SemanticJson.hash(d.preservedValue())+d.origin()));
        assertEquals(old,current);assertEquals(legacy.source(),canonical.source());
    }
    @Test void configurationVersionUnknownFieldsAndDuplicateJsonKeysCannotBypassAdmission() {
        var profile=JaCaMoMappingProfile.observedV1();var rules=rules("Counter",profile);
        var tree=new HashMap<>(CanonicalJson.object(CanonicalJson.decode(rules.bytes())));tree.put("version","1.0");
        assertThrows(IllegalArgumentException.class,()->MappingRuleSet.parse(CanonicalJson.encode(tree)));
        tree.put("version","2.0");tree.put("mysteryFlag",true);assertThrows(IllegalArgumentException.class,()->MappingRuleSet.parse(CanonicalJson.encode(tree)));
        assertThrows(IllegalArgumentException.class,()->MappingRuleSet.parse("{\"version\":\"2.0\",\"version\":\"1.0\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }
}
