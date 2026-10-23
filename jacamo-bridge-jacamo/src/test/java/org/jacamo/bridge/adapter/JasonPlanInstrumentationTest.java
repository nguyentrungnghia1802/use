package org.jacamo.bridge.adapter;
import java.nio.file.*;
import java.util.*;
import jason.asSemantics.*;
import jason.asSyntax.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
class JasonPlanInstrumentationTest {
    @TempDir Path temp;
    private Path source()throws Exception{return Files.writeString(temp.resolve("worker.asl"),"+!deliver <- .print(\"ready\"); .print(\"sent\").\n");}
    private Agent parse(Path file)throws Exception {
        jason.util.Config.get().setProperty(jason.util.Config.START_WEB_MI,"false");
        var agent=new Agent();agent.setConsiderToAddMIForThisAgent(false);agent.initAg();agent.setASLSrc(file.toUri().toURL().toString());
        try(var input=Files.newInputStream(file)){agent.parseAS(input,file.toUri().toURL().toString());}return agent;
    }
    @Test void arbitraryTwoStepCaseUsesExactOfficialAstAndRestoresDisposableBodies()throws Exception {
        var file=source();var source=new OfficialJasonAdapter().adapt(temp,file,"dispatch","worker").program();var originalBytes=Files.readAllBytes(file);
        var actor=parse(file);var calls=new ArrayList<String>();var original=actor.getPL().getPlans().getFirst().getBody();
        var reporter=new JasonPlanInstrumentation.Reporter(){public void marker(TransitionSystem ts,JasonPlanInstrumentation.Site site,String phase){calls.add(site.task()+":"+phase);}public void unavailable(String reason){fail(reason);}};
        var definition=new JasonPlanInstrumentation.Specification(JasonPlanInstrumentation.RECIPE,source.sourceDigest(),List.of(new JasonPlanInstrumentation.PlanSelection(source.planLibrary().plans().getFirst().metadata().semanticId(),List.of(new JasonPlanInstrumentation.Boundary("PrepareDispatch",0,1),new JasonPlanInstrumentation.Boundary("SendDispatch",1,2)))));
        var ts=new TransitionSystem(actor,new Circumstance(),new jason.runtime.Settings(),new jason.architecture.AgArch());
        try(var prepared=JasonPlanInstrumentation.prepare(actor,source,temp,reporter,definition)) {
            assertSame(original,actor.getPL().getPlans().getFirst().getBody());assertEquals(2,prepared.sites().size());prepared.install();
            ts.getC().addAchvGoal(ASSyntax.parseLiteral("deliver"),Intention.EmptyInt);for(int i=0;i<30;i++)ts.reasoningCycle();
            assertEquals(List.of("PrepareDispatch:ENABLED","PrepareDispatch:SUCCEEDED","SendDispatch:ENABLED","SendDispatch:SUCCEEDED"),calls);
            assertEquals(JasonPlanInstrumentation.RECIPE,prepared.manifest().get("recipe"));assertArrayEquals(originalBytes,Files.readAllBytes(file));
        }finally{actor.stopAg();}
        assertSame(original,actor.getPL().getPlans().getFirst().getBody());
    }
    @Test void badPartitionsUnresolvedPlansAndAstDriftCannotPartiallyInstall()throws Exception {
        var file=source();var source=new OfficialJasonAdapter().adapt(temp,file,"dispatch","worker").program();var actor=parse(file);
        var reporter=new JasonPlanInstrumentation.Reporter(){public void marker(TransitionSystem ts,JasonPlanInstrumentation.Site site,String phase){}public void unavailable(String reason){}};
        try {
            assertThrows(IllegalArgumentException.class,()->new JasonPlanInstrumentation.PlanSelection("plan",List.of(new JasonPlanInstrumentation.Boundary("Task",1,2))));
            var definition=new JasonPlanInstrumentation.Specification(JasonPlanInstrumentation.RECIPE,source.sourceDigest(),List.of(new JasonPlanInstrumentation.PlanSelection("unresolved",List.of(new JasonPlanInstrumentation.Boundary("Task",0,2)))));
            assertThrows(IllegalArgumentException.class,()->JasonPlanInstrumentation.prepare(actor,source,temp,reporter,definition));
            var actual=new JasonPlanInstrumentation.Specification(JasonPlanInstrumentation.RECIPE,source.sourceDigest(),List.of(new JasonPlanInstrumentation.PlanSelection(source.planLibrary().plans().getFirst().metadata().semanticId(),List.of(new JasonPlanInstrumentation.Boundary("Task",0,2)))));
            actor.getPL().getPlans().getFirst().getBody().setBodyTerm(ASSyntax.parseTerm(".fail"));
            assertThrows(IllegalArgumentException.class,()->JasonPlanInstrumentation.prepare(actor,source,temp,reporter,actual));
        }finally{actor.stopAg();}
    }
}
