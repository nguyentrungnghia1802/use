package org.tzi.use.plugins.jacamo.semantics;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.jacamo.bridge.adapter.*;
import org.tzi.use.plugins.jacamo.codegrounded.*;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.*;
import org.tzi.use.plugins.jacamo.codegrounded.use.*;
import org.tzi.use.plugins.jacamo.semantics.csl.*;
import org.tzi.use.plugins.jacamo.semantics.mapping.*;
import org.tzi.use.plugins.jacamo.semantics.profile.*;
import org.tzi.use.plugins.jacamo.semantics.istar.*;
import org.tzi.use.plugins.jacamo.semantics.evidence.*;

class JasonTaskConfigurationTest {
    @Test void productionConfigurationAdmitsExactSelectedStepButReportsMissingRuntimeCausation()throws Exception {
        var dir=Files.createTempDirectory(Path.of("target"),"jason-task-config-");Files.createDirectories(dir.resolve("src/agt"));Files.createDirectories(dir.resolve("src/org"));Files.copy(Path.of("src/test/resources/semantics/profile-v1/monitor.jcm"),dir.resolve("monitor.jcm"));Files.copy(Path.of("src/test/resources/semantics/profile-v1/src/org/monitor.xml"),dir.resolve("src/org/monitor.xml"));Files.writeString(dir.resolve("src/agt/a.asl"),"ready.\n+!work : ready <- increment; .print(done).\n");var path=dir.resolve("monitor.jcm").toAbsolutePath();var source=new OfficialProjectAdapter().adapt(new OfficialProjectLoader().load(path),path);
        var program=source.semanticContract().jasonPrograms().getFirst();var plan=program.planLibrary().plans().stream().filter(p->p.trigger().literal().equals("work")).findFirst().orElseThrow();var action=plan.body().stream().filter(b->b.bodyType().equals("action")).findFirst().orElseThrow();
        var domain=new CslSchema.Classifier("domain",DomainProjection.artifactId(CslCounterArtifact.class.getName()),"Counter",CslSchema.Kind.ENTITY,"",false,List.of(new CslSchema.Attribute("p","p","Integer","p",false)));var contract=new CslSchema.OperationContract("increment","domain",List.of(CslCounterArtifact.class.getName(),"increment",List.of(),"void"),"true","self.p = self.p@pre + 1");var base=new OrganizationalMappingRules().derive(source,List.of(domain),List.of(),List.of(),List.of(contract),new RequirementModel(List.of()));var role=base.schema().classifiers().stream().filter(c->c.kind()==CslSchema.Kind.ROLE && !c.abstractType()).findFirst().orElseThrow();var occurrence=new RequirementModel.Binding("actor",source.semanticContract().agentDeclarations().getFirst().metadata().semanticId(),role.id(),"runtime:context","runtime:counter","domain","episode");
        var selected=new TaskBinding(program.metadata().semanticId(),plan.metadata().semanticId(),action.metadata().semanticId(),contract.id(),occurrence,new JasonGroundQueryCompiler().compile(plan.context()));var config=new CslMonitorConfiguration(base.version(),base.profile(),base.schema(),new RequirementModel(List.of(selected.requirement("task","SourceWork"))),base.decisions(),List.of(selected));assertEquals(config,CslMonitorConfiguration.parse(config.bytes()));var built=new CodeGroundedNativePipeline().build(source,NativeProjectionMode.AUTO,config);assertTrue(built.model().model().getAllAnnotations().keySet().stream().anyMatch(k->k.startsWith("JasonTask_")));
        try(var projector=new NativeRuntimeProjector(built,"task-config",1,source.modelRevision())) {var row=projector.coordinator().latest().semantic().constraints().stream().filter(r->r.id().equals("REQUIREMENT:task")).findFirst().orElseThrow();assertEquals(SemanticVerdict.UNSUPPORTED,row.verdict());assertEquals("JASON_ACTION_TO_CARTAGO_INVOCATION_CAUSATION_UNAVAILABLE",row.diagnostic());assertEquals(IntentionalMarking.Status.UNDETERMINED,projector.coordinator().latest().semantic().requirements().get("task").evaluated());}
        var wrong=new TaskBinding(program.metadata().semanticId(),plan.metadata().semanticId(),action.metadata().semanticId(),contract.id(),occurrence,new JasonGroundQueryCompiler().compile("other"));var invalid=new CslMonitorConfiguration(config.version(),config.profile(),config.schema(),config.requirements(),config.decisions(),List.of(wrong));assertThrows(IllegalArgumentException.class,()->new CodeGroundedNativePipeline().build(source,NativeProjectionMode.AUTO,invalid));
    }
}
