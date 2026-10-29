package org.tzi.use.plugins.jacamo.semantics.auction;

import java.nio.file.*;
import java.util.*;
import jason.asSemantics.*;
import jason.asSyntax.*;
import org.jacamo.bridge.adapter.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import static org.junit.jupiter.api.Assertions.*;

class AuctionStagingInstrumentationTest {
    static final Path ROOT=Path.of("examples/auction").toAbsolutePath();
    @BeforeAll static void officialPackageRoots()throws Exception {new OfficialProjectLoader().load(ROOT.resolve("auction.jcm"));}
    static final AuctionStagingInstrumentation.Reporter REPORTER=new AuctionStagingInstrumentation.Reporter(){public void marker(TransitionSystem ts,AuctionStagingInstrumentation.Site site,String phase){}public void unavailable(String diagnostic){}};
    @Test void sixSitesWrapOnlyDisposableBodiesAndCloseRestoresTheOriginalPlanObjects()throws Exception {
        var source=new OfficialJasonAdapter().adapt(ROOT,ROOT.resolve("src/agt/auction_capabilities.asl"),"auction","bob").program();
        assertTrue(source.planLibrary().plans().stream().anyMatch(p->p.trigger().literal().startsWith("fulfill_obligation(")),"The actual org-obedient include must be parsed, not silently absent");
        var agent=parse();var original=new LinkedHashMap<Plan,PlanBody>();for(var plan:agent.getPL())original.put(plan,plan.getBody());var bytes=Files.readAllBytes(ROOT.resolve("src/agt/auction_capabilities.asl"));
        try(var prepared=AuctionStagingInstrumentation.prepare(agent,source,ROOT,REPORTER)) {
            assertEquals(Set.of("A_start_t1","A_start_t2","A_decide_t1","A_decide_t2","P_bid_t1","P_bid_t2"),prepared.sites().stream().map(AuctionStagingInstrumentation.Site::task).collect(java.util.stream.Collectors.toSet()));
            assertTrue(original.entrySet().stream().allMatch(e->e.getKey().getBody()==e.getValue()),"Preparation must be read-only");
            prepared.install();assertEquals(3,original.entrySet().stream().filter(e->e.getKey().getBody()!=e.getValue()).count());
            assertTrue(prepared.manifest().get("plans") instanceof List<?> plans&&plans.size()==3);assertTrue(prepared.hash().matches("[0-9a-f]{64}"));
            assertThrows(UnsupportedOperationException.class,()->prepared.manifest().put("recipe","forged"));
            assertArrayEquals(bytes,Files.readAllBytes(ROOT.resolve("src/agt/auction_capabilities.asl")));
            assertThrows(IllegalArgumentException.class,prepared::install);
        }finally{agent.stopAg();}
        assertTrue(original.entrySet().stream().allMatch(e->e.getKey().getBody()==e.getValue()));
    }
    @Test void actualBodyDriftIsRejectedBeforeAnyPlanIsChanged()throws Exception {
        var source=new OfficialJasonAdapter().adapt(ROOT,ROOT.resolve("src/agt/auction_capabilities.asl"),"auction","bob").program();var agent=parse();
        try {
            var bid=agent.getPL().getPlans().stream().filter(p->p.getTrigger().getLiteral().getFunctor().equals("bid")).findFirst().orElseThrow();
            bid.getBody().getLastBody().setBodyTerm(ASSyntax.parseTerm(".fail"));var before=agent.getPL().toString();
            assertTrue(assertThrows(IllegalArgumentException.class,()->AuctionStagingInstrumentation.prepare(agent,source,ROOT,REPORTER)).getMessage().contains("SOURCE_AST_CHANGED"));assertEquals(before,agent.getPL().toString());
        }finally{agent.stopAg();}
    }
    @Test void changedPlanBetweenPreparationAndInstallationCannotLeaveHalfInstrumentation()throws Exception {
        var source=new OfficialJasonAdapter().adapt(ROOT,ROOT.resolve("src/agt/auction_capabilities.asl"),"auction","bob").program();var agent=parse();
        try(var prepared=AuctionStagingInstrumentation.prepare(agent,source,ROOT,REPORTER)) {
            var start=agent.getPL().getPlans().stream().filter(p->p.getTrigger().getLiteral().getFunctor().equals("start")).findFirst().orElseThrow();
            start.getBody().getLastBody().setBodyTerm(ASSyntax.parseTerm(".fail"));var before=agent.getPL().toString();
            assertThrows(IllegalArgumentException.class,prepared::install);assertEquals(before,agent.getPL().toString());
        }finally{agent.stopAg();}
    }
    @Test void pendingInitialGoalsAreAllowedButAnInstantiatedIntendedMeansIsRejected()throws Exception {
        var source=new OfficialJasonAdapter().adapt(ROOT,ROOT.resolve("src/agt/auction_capabilities.asl"),"auction","bob").program();var agent=parse();
        var ts=new TransitionSystem(agent,new Circumstance(),new jason.runtime.Settings(),new jason.architecture.AgArch());
        ts.getC().addAchvGoal(ASSyntax.parseLiteral("start[scheme(\"pending\")]"),Intention.EmptyInt);
        try(var prepared=AuctionStagingInstrumentation.prepare(agent,source,ROOT,REPORTER)){prepared.install();}
        var intention=new Intention();var plan=agent.getPL().getPlans().stream().filter(p->p.getTrigger().getLiteral().getFunctor().equals("start")).findFirst().orElseThrow();
        intention.push(new IntendedMeans(new Option(plan,new Unifier()),plan.getTrigger()));ts.getC().addRunningIntention(intention);
        try(var prepared=AuctionStagingInstrumentation.prepare(agent,source,ROOT,REPORTER)){assertTrue(assertThrows(IllegalArgumentException.class,prepared::install).getMessage().contains("REASONING_ALREADY_STARTED"));}
        finally{agent.stopAg();}
    }
    @Test void unavailableObserverCannotTurnTheOriginalPlanIntoAFailedExecution()throws Exception {
        var source=new OfficialJasonAdapter().adapt(ROOT,ROOT.resolve("src/agt/auction_capabilities.asl"),"auction","fixture").program();var agent=parse();
        var arch=new AuctionStepObservationTest.FixtureArchitecture();var ts=new TransitionSystem(agent,new Circumstance(),new jason.runtime.Settings(),arch);var diagnostics=new ArrayList<String>();
        var reporter=new AuctionStagingInstrumentation.Reporter(){public void marker(TransitionSystem actual,AuctionStagingInstrumentation.Site site,String phase){throw new IllegalStateException("EXPLICIT_OBSERVER_FAILURE_FIXTURE");}public void unavailable(String diagnostic){diagnostics.add(diagnostic);}};
        try(var prepared=AuctionStagingInstrumentation.prepare(agent,source,ROOT,reporter)) {
            prepared.install();agent.getBB().add(ASSyntax.parseLiteral("goalArgument(\"s\",auction,\"Id\",\"a\")"));agent.getBB().add(ASSyntax.parseLiteral("goalArgument(\"s\",auction,\"Service\",\"service\")"));
            ts.getC().addAchvGoal(ASSyntax.parseLiteral("start[scheme(\"s\")]"),Intention.EmptyInt);for(int i=0;i<50;i++)ts.reasoningCycle();
            assertTrue(arch.executed.stream().anyMatch(a->a.startsWith("start(")));assertEquals(4,diagnostics.size());
        }finally{agent.stopAg();}
    }
    static Agent parse()throws Exception {
        jason.util.Config.get().setProperty(jason.util.Config.START_WEB_MI,"false");
        var agent=new Agent();agent.setConsiderToAddMIForThisAgent(false);agent.initAg();var file=ROOT.resolve("src/agt/auction_capabilities.asl").toUri().toURL();agent.setASLSrc(file.toString());
        try(var input=file.openStream()){agent.parseAS(input,file.toString());}return agent;
    }
}
