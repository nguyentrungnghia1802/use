package org.tzi.use.plugins.jacamo.semantics.auction;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.jacamo.bridge.adapter.*;
import org.jacamo.bridge.contract.ModelSnapshot;
import org.junit.jupiter.api.Test;
import org.tzi.use.plugins.jacamo.codegrounded.CodeGroundedNativePipeline;
import org.tzi.use.plugins.jacamo.codegrounded.use.NativeProjectionMode;
import org.tzi.use.plugins.jacamo.semantics.mapping.*;
import org.tzi.use.plugins.jacamo.semantics.mapping.auction.*;
import org.tzi.use.semantic.engine.api.*;
import static org.junit.jupiter.api.Assertions.*;

class AuctionStaticProgramTest {
    @Test void casePlanCannotBeMixedWithAnotherStaticBindingOrRulesIdentity()throws Exception {
        try(var fixture=new AuctionLiveFixture()) {
            var program=new AuctionStaticMappingAdapter().map(fixture.source,new AuctionStaticMappingAdapter.Selection(fixture.bundle,fixture.specification));
            assertThrows(IllegalArgumentException.class,()->new SemanticProgram(program.profile(),program.rules(),program.source(),program.cslModel(),program.goalModel(),new SemanticBindingIndex(List.of()),program.complement(),List.of(),program.goalBindings(),program.casePlan()));
            var rules=program.rules();
            var forged=new MappingRuleSet(rules.version(),rules.id(),rules.profileId(),rules.profileVersion(),"0".repeat(64),rules.cslSource(),rules.iStarSource(),rules.bindings(),rules.contracts(),rules.decisions(),rules.options());
            assertThrows(IllegalArgumentException.class,()->new SemanticProgram(program.profile(),forged,program.source(),program.cslModel(),program.goalModel(),program.bindings(),program.complement(),List.of(),program.goalBindings(),program.casePlan()));
        }
    }
    @Test void programOwnsOriginalModelsFrozenAssetsResolvedBindingsAndSeparateNativeContracts()throws Exception {
        try(var fixture=new AuctionLiveFixture()) {
            var selection=new AuctionStaticMappingAdapter.Selection(fixture.bundle,fixture.specification);
            var adapter=new AuctionStaticMappingAdapter();var program=adapter.map(fixture.source,selection);
            assertEquals(program.hash(),adapter.map(fixture.source,selection).hash());assertEquals(6,program.casePlan().tasks().size());
            assertEquals(7,program.goalBindings().elements().values().stream().filter(GoalRuntimeBindingPlan.ElementTemplate::derived).count());
            assertEquals(3,program.goalModel().getDependencies().size());assertEquals(12,program.goalModel().getActors().stream().flatMap(a->a.elements().stream()).filter(e->e instanceof org.vnu.sme.goal.dsl.istar.mm.Task).mapToInt(e->((org.vnu.sme.goal.dsl.istar.mm.Task)e).constraints().size()).sum());
            assertTrue(program.contracts().isEmpty(),"Operation clauses are not relabelled as historical iStar Task contracts");
            assertEquals(fixture.bundle.assets().size()+3,program.complement().assets().size());
            assertTrue(program.bindings().bindings().stream().allMatch(b->program.source().nodes().stream().anyMatch(n->n.id().equals(b.sourceId()))));
            assertTrue(program.source().nodes().stream().noneMatch(n->n.sourceKind().equals("JACAMO_ARTIFACT_DECLARATION")));
            var previous=Thread.currentThread().getContextClassLoader();
            try {
                Thread.currentThread().setContextClassLoader(new ClassLoader(null){});
                var built=new CodeGroundedNativePipeline().buildCanonicalProgram(fixture.source,NativeProjectionMode.AUTO,program);
                assertSame(program.cslModel(),built.binding().cslModel());assertSame(built.binding().model(),built.nativeResult().state().system().model());
                assertEquals(3,built.caseOperations().size());assertEquals(8,built.caseContracts().clauses().size());assertTrue(built.contracts().contracts().isEmpty());
                assertEquals(0,built.nativeResult().state().system().state().numObjects());
            }finally{Thread.currentThread().setContextClassLoader(previous);}
            var wrongProfile=new JaCaMoMappingProfile(program.profile().id(),"99.0",program.profile().engine(),program.profile().capabilities());
            assertThrows(IllegalArgumentException.class,()->new SemanticProgram(wrongProfile,program.rules(),program.source(),program.cslModel(),program.goalModel(),program.bindings(),program.complement(),List.of(),program.goalBindings(),program.casePlan()));
            var task=(org.vnu.sme.goal.dsl.istar.mm.Task)program.goalModel().findElement("A_start_t1").orElseThrow();task.setId("changed");
            assertThrows(IllegalStateException.class,program::assertUnchanged);
        }
    }

    @Test void onlyTheExactBridgeBootstrapInsertionCanQualifyAStagedJcm()throws Exception {
        try(var fixture=new AuctionLiveFixture()) {
            var directory=AuctionImportTest.copyCase(fixture.bundle);var jcm=directory.resolve("auction.jcm");
            String original=new String(fixture.bundle.require("src-jcm").bytes(),StandardCharsets.UTF_8);
            String insertion="    platform: org.jacamo.bridge.adapter.JaCaMoBridgePlatform(\"port=6553\", \"secretFile="+directory.resolve("bridge.secret").toString().replace('\\','/')+"\", \"jcmFile="+jcm.toString().replace('\\','/')+"\", \"distributionSha256="+"1".repeat(64)+"\")"+System.lineSeparator();
            int closing=original.lastIndexOf('}');String staged=original.substring(0,closing)+insertion+original.substring(closing);Files.writeString(jcm,staged);
            var source=official(jcm);var selection=new AuctionStaticMappingAdapter.Selection(fixture.bundle,fixture.specification);
            assertThrows(IllegalArgumentException.class,()->new AuctionSourceSelector().execute(fixture.specification,fixture.bundle,source));
            assertThrows(IllegalArgumentException.class,()->new AuctionStaticMappingAdapter().map(source,selection));
            var program=new AuctionStaticMappingAdapter().map(source,selection,jcm);
            assertEquals("BRIDGE_PLATFORM_INSERTION/1.0",((Map<?,?>)program.casePlan().evidence().get("jcmDerivation")).get("recipe"));
            assertArrayEquals(Files.readAllBytes(jcm),program.complement().assets().stream().filter(a->a.id().equals("src-jcm:derived")).findFirst().orElseThrow().bytes());
            assertEquals(new AuctionCslRuleExecutor().generate(fixture.specification,fixture.bundle,fixture.selectors).fingerprint(),program.cslHash());
            var session=new org.tzi.use.main.Session();try(var facade=AuctionImportTest.facade(source,session)) {
                facade.importProject(jcm,new org.tzi.use.plugins.jacamo.JaCaMoFacade.ImportSelection("AUCTION_OBSERVED_V1",Path.of("examples/auction/auction_jacamo_to_csl_rules.xml")));
                assertEquals(program.hash(),facade.semanticProgramEvidence().get("programHash"));
            }
            for(String changed:List.of(staged.replace("port=6553","port=0"),staged.replace("agent bob :","agent somebody :"),staged.replace(insertion,insertion+insertion),staged.replace("jcmFile="+jcm.toString().replace('\\','/'),"jcmFile="+directory.resolve("other.jcm").toString().replace('\\','/')))) {
                Files.writeString(jcm,changed);var actual=official(jcm);
                assertThrows(IllegalArgumentException.class,()->AuctionJcmDerivation.read(fixture.bundle,actual,jcm));
            }
            assertArrayEquals(fixture.bundle.require("src-jcm").bytes(),Files.readAllBytes(Path.of("examples/auction/auction.jcm")));
        }
    }
    private static ModelSnapshot official(Path jcm)throws Exception{return new OfficialProjectAdapter().adapt(new OfficialProjectLoader().load(jcm),jcm);}
}
