package org.tzi.use.plugins.jacamo.semantics;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.jacamo.bridge.adapter.*;
import org.tzi.use.plugins.jacamo.semantics.mapping.*;
import org.tzi.use.plugins.jacamo.semantics.istar.*;
import org.tzi.use.plugins.jacamo.semantics.csl.*;

class JasonAbstractionPolicyTest {
    @Test void sourceStepNeedsExactProgramPlanBodyContextAndTargetInvocationContract()throws Exception {
        var directory=Files.createTempDirectory(Path.of("target"),"jason-fragment-");var path=directory.resolve("agent.asl");Files.writeString(path,"ready.\n+!work : ready <- increment; .print(done); !again.\n");var program=new OfficialJasonAdapter().adapt(directory,path,"fragment","a").program();var plan=program.planLibrary().plans().stream().filter(p->p.trigger().literal().equals("work")).findFirst().orElseThrow();var action=plan.body().stream().filter(b->b.bodyType().equals("action")).findFirst().orElseThrow();
        var source=SemanticTransformationTest.source();var domain=new CslSchema.Classifier("domain",org.tzi.use.plugins.jacamo.codegrounded.use.DomainProjection.artifactId(CslCounterArtifact.class.getName()),"Counter",CslSchema.Kind.ENTITY,"",false,List.of(new CslSchema.Attribute("p","p","Integer","p",false)));var contract=new CslSchema.OperationContract("increment","domain",List.of(CslCounterArtifact.class.getName(),"increment",List.of(),"void"),"self.p >= 0","self.p = self.p@pre + 1");
        var config=new OrganizationalMappingRules().derive(source,List.of(domain),List.of(),List.of(),List.of(contract),new RequirementModel(List.of()));var binding=new TaskBinding(program.metadata().semanticId(),plan.metadata().semanticId(),action.metadata().semanticId(),"increment",TemporalGoalMonitorTest.B);var policy=new JasonAbstractionPolicy();var approved=policy.admit(program,binding,new JasonGroundQueryCompiler().compile(plan.context()),config);assertEquals(plan.body().size()+2,approved.decisions().size());assertEquals(1,approved.decisions().stream().filter(d->d.disposition().equals("APPROVED_ACTION_STEP")).count());assertTrue(approved.contextOcl().contains("storedBeliefs"));assertEquals(RequirementModel.Kind.TASK,binding.requirement("task","task").kind());
        var internal=plan.body().stream().filter(b->b.bodyType().equals("internalAction")).findFirst().orElseThrow();assertThrows(IllegalArgumentException.class,()->policy.admit(program,new TaskBinding(program.metadata().semanticId(),plan.metadata().semanticId(),internal.metadata().semanticId(),"increment",TemporalGoalMonitorTest.B),new JasonGroundQueryCompiler().compile(plan.context()),config));
        assertThrows(IllegalArgumentException.class,()->policy.admit(program,binding,new JasonGroundQueryCompiler().compile("other"),config));assertThrows(IllegalArgumentException.class,()->policy.admit(program,new TaskBinding(program.metadata().semanticId(),plan.metadata().semanticId(),action.metadata().semanticId(),"missing",TemporalGoalMonitorTest.B),new JasonGroundQueryCompiler().compile(plan.context()),config));
    }
}
