package org.jacamo.bridge.adapter;
import java.nio.file.*;
import java.util.*;
import jason.asSemantics.*;
import jason.asSyntax.*;
import org.jacamo.bridge.contract.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
class JasonStepObservationTest {
    @TempDir Path temp;
    @Test void neutralListenerPublishesActualMarkersAndRetiresAllState()throws Exception {
        var path=Files.writeString(temp.resolve("agent.asl"),"+!deliver <- .print(\"ready\"); .print(\"sent\").\n");
        var source=new OfficialJasonAdapter().adapt(temp,path,"dispatch","worker").program();
        jason.util.Config.get().setProperty(jason.util.Config.START_WEB_MI,"false");var actor=new Agent();actor.setConsiderToAddMIForThisAgent(false);actor.initAg();actor.setASLSrc(path.toUri().toString());
        try(var input=Files.newInputStream(path)){actor.parseAS(input,path.toUri().toString());}
        var ts=new TransitionSystem(actor,new Circumstance(),new jason.runtime.Settings(),new jason.architecture.AgArch(){@Override public String getAgName(){return "worker";}});
        var definition=new JasonPlanInstrumentation.Specification(JasonPlanInstrumentation.RECIPE,source.sourceDigest(),List.of(new JasonPlanInstrumentation.PlanSelection(source.planLibrary().plans().getFirst().metadata().semanticId(),List.of(new JasonPlanInstrumentation.Boundary("Ready",0,1),new JasonPlanInstrumentation.Boundary("Send",1,2)))));
        var frames=new ArrayList<VerificationObservation>();var environment=cartago.CartagoEnvironment.getInstance();environment.init();var original=actor.getPL().getPlans().getFirst().getBody();
        var captures=List.of(new JasonStepObservation.Capture("Ready",List.of(),"","","",false,List.of()),new JasonStepObservation.Capture("Send",List.of(),"","","",false,List.of()));
        try(var observer=new JasonStepObservation(ts,new BridgeEntityId("jason","agent","runtime-agent","dispatch","worker","incarnation"),"run",1,"revision",source,temp,frames::add,reason->fail(reason),definition,captures)) {
            ts.getC().addAchvGoal(ASSyntax.parseLiteral("deliver"),Intention.EmptyInt);for(int i=0;i<30;i++)ts.reasoningCycle();
            assertEquals(List.of("ENABLED","SUCCEEDED","ENABLED","SUCCEEDED"),frames.stream().map(f->f.data().get("phase")).toList());
            assertTrue(frames.stream().allMatch(f->f.quality()==Completeness.COMPLETE));assertEquals(0,observer.activeGoals());
            var entry=CanonicalJson.object(CanonicalJson.object(frames.getFirst().data().get("bindings")).get("stepValueQuality"));
            assertEquals("JASON_STEP_VALUES/2.0",entry.get("version"));assertEquals("UNAVAILABLE",entry.get("succeeded"));
            assertEquals(2,frames.stream().map(f->f.data().get("stepInvocation")).distinct().count());
        }finally{actor.stopAg();environment.shutdown();}
        assertSame(original,actor.getPL().getPlans().getFirst().getBody());assertTrue(ts.getGoalListeners().stream().noneMatch(JasonStepObservation.class::isInstance));
    }
    @Test void actualFailureCarriesExactCurrentSourceInstructionRatherThanInferringFromTaskName()throws Exception {
        var path=Files.writeString(temp.resolve("decline.asl"),"+!deliver <- if (true) { .fail }.\n");
        var source=new OfficialJasonAdapter().adapt(temp,path,"dispatch","worker").program();
        var actor=new Agent();actor.setConsiderToAddMIForThisAgent(false);actor.initAg();actor.setASLSrc(path.toUri().toString());
        try(var input=Files.newInputStream(path)){actor.parseAS(input,path.toUri().toString());}
        var ts=new TransitionSystem(actor,new Circumstance(),new jason.runtime.Settings(),new jason.architecture.AgArch(){@Override public String getAgName(){return "worker";}});
        var definition=new JasonPlanInstrumentation.Specification(JasonPlanInstrumentation.RECIPE,source.sourceDigest(),List.of(new JasonPlanInstrumentation.PlanSelection(source.planLibrary().plans().getFirst().metadata().semanticId(),List.of(new JasonPlanInstrumentation.Boundary("Attempt",0,1)))));
        var frames=new ArrayList<VerificationObservation>();var environment=cartago.CartagoEnvironment.getInstance();environment.init();
        try(var observer=new JasonStepObservation(ts,new BridgeEntityId("jason","agent","runtime-agent","dispatch","worker","incarnation"),"run",1,"revision",source,temp,frames::add,reason->fail(reason),definition,List.of(new JasonStepObservation.Capture("Attempt",List.of(),"","","",false,List.of())))) {
            ts.getC().addAchvGoal(ASSyntax.parseLiteral("deliver"),Intention.EmptyInt);for(int i=0;i<20;i++)ts.reasoningCycle();
            assertEquals(List.of("ENABLED","FAILED"),frames.stream().map(f->f.data().get("phase")).toList());
            var bindings=CanonicalJson.object(frames.getLast().data().get("bindings"));var proof=CanonicalJson.object(bindings.get("failureSite"));
            assertEquals("ACTUAL_INTENDED_MEANS_CURRENT_INSTRUCTION/1.0",proof.get("authority"));
            assertEquals(".fail",proof.get("term"));assertEquals("internalAction",proof.get("bodyType"));
            assertEquals(1,((Number)proof.get("line")).intValue());
            assertEquals(source.sourceDigest(),proof.get("sourceHash"));
        }finally{actor.stopAg();environment.shutdown();}
    }

}
