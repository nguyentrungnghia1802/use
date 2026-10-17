package org.tzi.use.plugins.jacamo.semantics;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.jacamo.bridge.adapter.*;
import org.tzi.use.plugins.jacamo.codegrounded.*;
import org.tzi.use.plugins.jacamo.codegrounded.use.*;
import org.tzi.use.plugins.jacamo.semantics.csl.*;
import org.tzi.use.plugins.jacamo.semantics.istar.RequirementModel;
import org.tzi.use.plugins.jacamo.semantics.profile.*;

class CslNativeBindingTest {
    @Test void explicitCslBindingUsesOneNativeModelPreservesInvariantsAndNoDeclarationPopulation() throws Exception {
        Path jcm=Path.of("src/test/resources/canonical-cases/hello-world/helloworld.jcm").toAbsolutePath();
        var snapshot=new OfficialProjectAdapter().adapt(new OfficialProjectLoader().load(jcm),jcm);
        var contract=snapshot.semanticContract();
        var os=contract.moiseOrganizations().getFirst();
        var group=os.structuralSpecification().groups().getFirst();
        var role=os.structuralSpecification().roles().stream().filter(r->!r.abstractRole()).findFirst().orElseThrow();
        var artifact=contract.artifactDeclarations().getFirst();
        var schema=new CslSchema("bound",List.of(
                new CslSchema.Classifier("context",group.metadata().semanticId(),"C",CslSchema.Kind.ORG_CONTEXT,"",false,List.of()),
                new CslSchema.Classifier("role",role.metadata().semanticId(),"R",CslSchema.Kind.ROLE,"",false,List.of()),
                new CslSchema.Classifier("domain",DomainProjection.artifactId(artifact.javaClass()),"X",CslSchema.Kind.ENTITY,"",false,
                        List.of(new CslSchema.Attribute("p","p","Integer","p",false)))),
                List.of(),List.of(new CslSchema.Invariant("nonnegative","domain","Nonnegative","self.p >= 0")),List.of());
        var config=new CslMonitorConfiguration("1.0",SemanticProjectionProfile.CSL_OBSERVER_V1,schema,new RequirementModel(List.of()),List.of());
        var result=new CodeGroundedNativePipeline().build(snapshot,NativeProjectionMode.AUTO,config);
        assertSame(result.model().model(),result.state().system().model());
        assertTrue(result.state().system().state().allObjects().isEmpty(),"JCM declarations are not live runtime population");
        assertEquals(1,result.model().model().classInvariants().size(),"Authored invariant must be registered");
        assertEquals("Integer",result.model().model().getClass("X").attribute("p",true).type().toString());
        assertTrue(result.model().model().getClass("AtomicAgent").allParents().stream().noneMatch(c->c.name().equals("DomainEntity")));
        assertTrue(result.model().model().getClass("R").allParents().stream().anyMatch(c->c.name().equals("RoleOccurrence")));
        assertEquals(config,CslUseBinding.configuration(result.export().recompiledModel()));
        assertDoesNotThrow(()->new NativeProjectionAudit().audit(result.model(),result.state().system()));
        var bad=new CslSchema("unresolved",List.of(new CslSchema.Classifier("bad","unknown-source-type","Wrong",
                CslSchema.Kind.ENTITY,"",false,List.of())),List.of(),List.of(),List.of());
        assertThrows(IllegalArgumentException.class,()->new CodeGroundedNativePipeline().build(snapshot,NativeProjectionMode.AUTO,
                new CslMonitorConfiguration("1.0",SemanticProjectionProfile.CSL_OBSERVER_V1,bad,new RequirementModel(List.of()),List.of())));
    }
    @Test void staticDeclaredInterfaceUsesActualOperationMethodWithoutRuntimePopulation() throws Exception {
        Path jcm=Path.of("src/test/resources/semantics/profile-v1/monitor.jcm").toAbsolutePath();
        var snapshot=new OfficialProjectAdapter().adapt(new OfficialProjectLoader().load(jcm),jcm);
        var schema=new CslSchema("static-interface",List.of(new CslSchema.Classifier("domain",DomainProjection.artifactId(CslCounterArtifact.class.getName()),"X",CslSchema.Kind.ENTITY,"",false,
            List.of(new CslSchema.Attribute("p","p","Integer","p",false)))),List.of(),List.of(),
            List.of(new CslSchema.OperationContract("increment","domain",List.of(CslCounterArtifact.class.getName(),"increment",List.of(),"void"),"true","self.p = self.p@pre + 1")));
        var result=new CodeGroundedNativePipeline().build(snapshot,NativeProjectionMode.AUTO,new CslMonitorConfiguration("1.0",SemanticProjectionProfile.CSL_OBSERVER_V1,schema,new RequirementModel(List.of()),List.of()));
        var operation=result.model().model().getClass("X").operations().iterator().next();
        assertEquals("REFLECTED_DECLARED_OPERATION",operation.getAnnotationValue("CslInterfaceAuthority","kind"));
        assertTrue(result.state().system().state().allObjects().isEmpty());
        assertThrows(IllegalArgumentException.class,()->DomainProjection.declaredOperation(new org.tzi.use.api.UseModelApi(result.model().model()),"X",List.of(CslCounterArtifact.class.getName(),"missing",List.of(),"void"),"missing"));
    }

}
