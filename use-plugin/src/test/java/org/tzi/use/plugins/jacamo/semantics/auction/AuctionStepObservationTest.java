package org.tzi.use.plugins.jacamo.semantics.auction;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import jason.architecture.AgArch;
import jason.asSemantics.*;
import jason.asSyntax.*;
import jason.runtime.Settings;
import org.jacamo.bridge.adapter.*;
import org.jacamo.bridge.contract.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Actual Jason interpreter/markers, explicit synthetic external-action fixture.
 * No Cartago/role/world proof is supplied: every frame must remain PARTIAL. */
class AuctionStepObservationTest {
    @BeforeAll static void packages()throws Exception {AuctionStagingInstrumentationTest.officialPackageRoots();}
    @Test void realQueryFailureHasAnExactFailedTriggerAndNoSuccessfulAfterMarker()throws Exception {
        try(var fixture=new Interpreter()) {
            fixture.goal("start[scheme(\"sch_one\")]");fixture.until(()->fixture.frames.stream().anyMatch(f->f.data().get("phase").equals("FAILED")));
            assertEquals(List.of("ENABLED","FAILED"),fixture.frames.stream().map(f->f.data().get("phase")).toList());
            assertEquals(1,fixture.frames.stream().map(f->f.data().get("goalInvocation")).distinct().count());
            assertEquals(1,fixture.frames.stream().map(f->f.data().get("stepInvocation")).distinct().count());
            assertTrue(CanonicalJson.object(fixture.frames.getLast().data().get("bindings")).get("sourceFailure").toString().startsWith("GOAL_LISTENER_FAILED:"));fixture.qualifiedMissing();
        }
    }
    @Test void successfulSourceQueriesAndActionsReachTheirOwnAfterMarkers()throws Exception {
        try(var fixture=new Interpreter()) {
            fixture.arguments("sch_one","a1");fixture.goal("start[scheme(\"sch_one\")]");fixture.until(()->fixture.frames.size()==4);
            assertEquals(List.of("A_start_t1:ENABLED","A_start_t1:SUCCEEDED","A_start_t2:ENABLED","A_start_t2:SUCCEEDED"),fixture.frames.stream().map(f->f.data().get("sourceStepId")+":"+f.data().get("phase")).toList());
            assertEquals(2,fixture.frames.stream().map(f->f.data().get("stepInvocation")).distinct().count());fixture.qualifiedMissing();
            assertTrue(fixture.owner.manifest().get("plans") instanceof List<?> plans&&plans.size()==3);
        }
    }
    @Test void originalRandomFailAndWaitBidBranchesAreExercisedWithOfficialPerAgentSeeds()throws Exception {
        for(boolean fail:new boolean[]{true,false})try(var fixture=new Interpreter()) {
            long seed=0;while((new Random(seed).nextDouble()>=0.9)!=fail)seed++;
            jason.stdlib.set_random_seed.getAgRandomIA(fixture.ts).setSeed(seed);
            fixture.arguments("sch_one","a1");fixture.goal("bid[scheme(\"sch_one\")]");fixture.until(()->fixture.frames.size()==4);
            assertEquals(List.of("P_bid_t1:ENABLED","P_bid_t1:SUCCEEDED","P_bid_t2:ENABLED","P_bid_t2:"+(fail?"FAILED":"SUCCEEDED")),fixture.frames.stream().map(f->f.data().get("sourceStepId")+":"+f.data().get("phase")).toList());
            assertEquals(fail?0:1,fixture.arch.executed.stream().filter(a->a.startsWith("bid(")).count());fixture.qualifiedMissing();
        }
    }
    @Test void twoInterleavedIntentionsHaveDifferentGoalAndStepInvocationsAndRetirementRemovesTheListener()throws Exception {
        var fixture=new Interpreter();try {
            fixture.arguments("sch_one","a1");fixture.arguments("sch_two","a2");fixture.goal("start[scheme(\"sch_one\")]");fixture.goal("start[scheme(\"sch_two\")]");
            fixture.until(()->fixture.frames.size()==8);
            assertEquals(2,fixture.frames.stream().map(f->f.data().get("goalInvocation")).distinct().count());assertEquals(4,fixture.frames.stream().map(f->f.data().get("stepInvocation")).distinct().count());fixture.qualifiedMissing();
            fixture.owner.close();assertFalse(fixture.ts.getGoalListeners().contains(fixture.owner));assertEquals(0,fixture.owner.activeGoals());
        }finally{fixture.close();}
    }
    @Test void focusingQuerySuccessFailureAndFailedExternalActionKeepTheirActualSourceOutcomes()throws Exception {
        for(boolean focusing:new boolean[]{false,true})try(var fixture=new Interpreter()) {
            fixture.arguments("sch_one","a1");
            if(focusing)fixture.agent.getBB().add(ASSyntax.parseLiteral("focusing(fixture_artifact,\"a1\",fixture,fixture,fixture,fixture)"));
            fixture.goal("decide[scheme(\"sch_one\")]");fixture.until(()->fixture.frames.size()==(focusing?4:2));
            assertEquals(focusing?List.of("ENABLED","SUCCEEDED","ENABLED","SUCCEEDED"):List.of("ENABLED","FAILED"),fixture.frames.stream().map(f->f.data().get("phase")).toList());fixture.qualifiedMissing();
        }
        try(var fixture=new Interpreter()) {
            fixture.arch.failedOperation="start";fixture.arguments("sch_one","a1");fixture.goal("start[scheme(\"sch_one\")]");fixture.until(()->fixture.frames.size()==4);
            var terminal=fixture.frames.getLast();assertEquals("FAILED",terminal.data().get("phase"));
            var receipts=(List<?>)CanonicalJson.object(terminal.data().get("bindings")).get("actions");assertEquals("FAILED",CanonicalJson.object(receipts.getLast()).get("result"));fixture.qualifiedMissing();
        }
    }
    static final class Interpreter implements AutoCloseable {
        final Agent agent=AuctionStagingInstrumentationTest.parse();final FixtureArchitecture arch=new FixtureArchitecture();
        final TransitionSystem ts=new TransitionSystem(agent,new Circumstance(),new Settings(),arch);
        final List<VerificationObservation> frames=new CopyOnWriteArrayList<>();final List<String> gaps=new CopyOnWriteArrayList<>();final AuctionStepObservation owner;
        Interpreter()throws Exception {
            var source=new OfficialJasonAdapter().adapt(AuctionStagingInstrumentationTest.ROOT,AuctionStagingInstrumentationTest.ROOT.resolve("src/agt/auction_capabilities.asl"),"auction","fixture").program();
            owner=new AuctionStepObservation(ts,new BridgeEntityId("jason","agent","runtime-agent","auction","fixture",UUID.randomUUID().toString()),"source-step-run",1,"source-step-revision",source,AuctionStagingInstrumentationTest.ROOT,frames::add,gaps::add);arch.owner=owner;
        }
        void arguments(String scheme,String id)throws Exception {agent.getBB().add(ASSyntax.parseLiteral("goalArgument(\""+scheme+"\",auction,\"Id\",\""+id+"\")"));agent.getBB().add(ASSyntax.parseLiteral("goalArgument(\""+scheme+"\",auction,\"Service\",\"test service\")"));}
        void goal(String goal)throws Exception {ts.getC().addAchvGoal(ASSyntax.parseLiteral(goal),Intention.EmptyInt);}
        void until(java.util.function.BooleanSupplier condition)throws Exception {long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(7);while(!condition.getAsBoolean()&&System.nanoTime()<end){ts.reasoningCycle();Thread.sleep(2);}assertTrue(condition.getAsBoolean(),"Actual Jason markers timed out: frames="+frames+" gaps="+gaps);assertTrue(gaps.isEmpty(),gaps.toString());}
        void qualifiedMissing(){assertTrue(frames.stream().allMatch(f->f.quality()==Completeness.PARTIAL&&f.diagnostic().equals("EXACT_SCHEME_PERCEPT_REFERENCE_UNAVAILABLE")),"Synthetic external actions cannot become authoritative Cartago/role observations");}
        public void close(){owner.close();agent.stopAg();}
    }
    static final class FixtureArchitecture extends AgArch {
        AuctionStepObservation owner;final List<String> executed=new ArrayList<>();int invocation;String failedOperation="";
        @Override public String getAgName(){return "source-step-fixture";}
        @Override public void act(ActionExec action) {
            String correlation="explicit-action-fixture:"+(++invocation);if(owner!=null)owner.action(action,RuntimeEventKind.STARTED,correlation);executed.add(action.getActionTerm().toString());
            var term=action.getActionTerm();var unifier=action.getIntention().peek().getUnif();
            if(term.getFunctor().equals("makeArtifact"))unifier.unifies(term.getTerm(3),ASSyntax.createAtom("fixture_artifact"));
            if(term.getFunctor().equals("lookupArtifact"))unifier.unifies(term.getTerm(1),ASSyntax.createAtom("fixture_artifact"));
            action.setResult(!term.getFunctor().equals(failedOperation));if(owner!=null)owner.action(action,action.getResult()?RuntimeEventKind.SUCCEEDED:RuntimeEventKind.FAILED,correlation);actionExecuted(action);
        }
    }
}
