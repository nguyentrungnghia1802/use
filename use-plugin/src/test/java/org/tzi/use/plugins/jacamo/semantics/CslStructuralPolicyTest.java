package org.tzi.use.plugins.jacamo.semantics;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.jacamo.bridge.adapter.*;
import org.tzi.use.api.*;
import org.tzi.use.plugins.jacamo.codegrounded.*;
import org.tzi.use.plugins.jacamo.codegrounded.use.*;
import org.tzi.use.plugins.jacamo.semantics.csl.*;
import org.tzi.use.plugins.jacamo.semantics.istar.RequirementModel;
import org.tzi.use.plugins.jacamo.semantics.profile.*;

class CslStructuralPolicyTest {
    static org.jacamo.bridge.contract.ModelSnapshot source()throws Exception {
        Path path=Path.of("src/test/resources/semantics/structural/structural.jcm").toAbsolutePath();
        return new OfficialProjectAdapter().adapt(new OfficialProjectLoader().load(path),path);
    }
    static List<CslSchema.Classifier> classifiers(org.jacamo.bridge.contract.ModelSnapshot source) {
        var ss=source.semanticContract().moiseOrganizations().getFirst().structuralSpecification();
        var types=new ArrayList<CslSchema.Classifier>();
        for(var group:ss.groups())types.add(new CslSchema.Classifier(group.groupId(),group.metadata().semanticId(),group.groupId().equals("team")?"C":"D",CslSchema.Kind.ORG_CONTEXT,"",false,List.of()));
        for(var role:ss.roles())if(!role.roleId().equals("soc"))types.add(new CslSchema.Classifier(role.roleId(),role.metadata().semanticId(),switch(role.roleId()){case "base"->"Base";case "worker"->"Worker";default->"Auditor";},
            CslSchema.Kind.ROLE,role.roleId().equals("worker")?"base":"",role.abstractRole(),role.roleId().equals("base")?List.of(new CslSchema.Attribute("rank","rank","Integer","rank",true)):List.of()));
        types.add(new CslSchema.Classifier("domain",DomainProjection.artifactId(CslCounterArtifact.class.getName()),"X",CslSchema.Kind.ENTITY,"",false,List.of()));return types;
    }
    static CodeGroundedNativePipeline.Result build(org.jacamo.bridge.contract.ModelSnapshot source,CslStructuralPolicy policy,List<CslSchema.Association> associations) {
        return new CodeGroundedNativePipeline().build(source,NativeProjectionMode.AUTO,new CslMonitorConfiguration("1.0",SemanticProjectionProfile.CSL_OBSERVER_V2,
            new CslSchema("structure",classifiers(source),associations,List.of(),List.of(),policy),new RequirementModel(List.of()),List.of()));
    }
    @Test void inheritanceEnactmentCompatibilityAndCardinalitiesUseOneRoleOccurrence()throws Exception {
        var source=source();var policy=new CslStructuralPolicy(List.of(new CslStructuralPolicy.Nesting("nest","team","subteam",0,1)),
            List.of(new CslStructuralPolicy.RoleCardinality("bound","team","base",1,1)),List.of(),true);
        var pipeline=build(source,policy,List.of());assertTrue(pipeline.state().system().state().allObjects().isEmpty());
        var api=UseSystemApi.create(pipeline.state().system(),false);
        org.tzi.use.uml.sys.MObject c=api.createObject("C","c"),d=api.createObject("D","d"),agent=api.createObject("AtomicAgent","a"),worker=api.createObject("Worker","r"),auditor=api.createObject("Auditor","q");
        api.createLink("Plays",agent.name(),worker.name());api.createLink("InContext",c.name(),worker.name());
        assertTrue(worker.cls().allParents().contains(pipeline.model().model().getClass("Base")));
        assertEquals(1,pipeline.state().system().state().objectsOfClassAndSubClasses(pipeline.model().model().getClass("Base")).size());
        api.setAttributeValue("r","rank","4");assertEquals("4",api.evaluate("r.rank").toString());
        assertEquals("true",api.evaluate("c.roles->select(r | r.oclIsKindOf(Base))->size() = 1").toString());
        var compatibility=pipeline.model().model().classInvariants().stream().filter(i->i.cls().name().equals("C") && i.getAnnotationValue("CslConstraint","id64").equals(DomainProjection.encode("compatibility:team"))).findFirst().orElseThrow();
        assertEquals("true",new org.tzi.use.uml.ocl.expr.Evaluator().eval(compatibility.expandedExpression(),pipeline.state().system().state()).toString());
        api.createLink("Plays",agent.name(),auditor.name());api.createLink("InContext",c.name(),auditor.name());
        assertEquals("false",new org.tzi.use.uml.ocl.expr.Evaluator().eval(compatibility.expandedExpression(),pipeline.state().system().state()).toString());
        api.deleteObject("q");
        // A context is a collective player using exactly the same context object.
        var worker2=api.createObject("Worker","r2");api.createLink("Plays",d.name(),worker2.name());api.createLink("InContext",c.name(),worker2.name());api.createLink("ContextNesting",c.name(),d.name());
        assertEquals("false",api.evaluate("c.roles->select(r | r.oclIsKindOf(Base))->size() <= 1").toString());
        assertEquals("true",api.evaluate("r2.player = d").toString());
        var third=api.createObject("Worker","r3");api.createLink("Plays",agent.name(),third.name());api.createLink("InContext",d.name(),third.name());
        assertEquals("true",api.evaluate("r.orgContext = c").toString());
        api.createObject("D","d2");api.createLink("ContextNesting",c.name(),"d2");
        var nestedBound=pipeline.model().model().classInvariants().stream().filter(i->i.getAnnotationValue("CslConstraint","id64").equals(DomainProjection.encode("nest"))).findFirst().orElseThrow();
        assertEquals("false",new org.tzi.use.uml.ocl.expr.Evaluator().eval(nestedBound.expandedExpression(),pipeline.state().system().state()).toString());
        assertTrue(pipeline.state().system().state().checkStructure(new java.io.PrintWriter(java.io.Writer.nullWriter())));
        var permitted=build(source,new CslStructuralPolicy(List.of(),List.of(),List.of(new CslStructuralPolicy.Compatibility("allow","worker","auditor")),true),List.of());
        var allowed=UseSystemApi.create(permitted.state().system(),false);allowed.createObject("C","c");allowed.createObject("AtomicAgent","a");
        allowed.createObject("Worker","r");allowed.createObject("Auditor","q");
        allowed.createLink("Plays","a","r");allowed.createLink("Plays","a","q");allowed.createLink("InContext","c","r");allowed.createLink("InContext","c","q");
        assertEquals("true",new org.tzi.use.uml.ocl.expr.Evaluator().eval(permitted.model().model().classInvariants().stream().filter(i->i.cls().name().equals("C")).findFirst().orElseThrow().expandedExpression(),permitted.state().system().state()).toString());
    }
    @Test void rejectsNestingCyclesKindsInvalidBoundsAndUnsupportedSourceParentProjection()throws Exception {
        var source=source();var types=classifiers(source);
        assertThrows(IllegalArgumentException.class,()->new CslSchema("bad",types,List.of(),List.of(),List.of(),new CslStructuralPolicy(List.of(new CslStructuralPolicy.Nesting("a","team","subteam"),new CslStructuralPolicy.Nesting("b","subteam","team")),List.of(),List.of(),false)));
        assertThrows(IllegalArgumentException.class,()->new CslSchema("duplicate-owner",types,List.of(),List.of(),List.of(),new CslStructuralPolicy(List.of(),List.of(),List.of(),false,List.of(new CslStructuralPolicy.Declaration("d1","team","worker"),new CslStructuralPolicy.Declaration("d2","subteam","worker")))));
        var wrong=types.stream().map(c->c.id().equals("worker")?new CslSchema.Classifier(c.id(),c.sourceId(),c.symbol(),c.kind(),"auditor",c.abstractType(),c.attributes()):c).toList();
        assertThrows(IllegalArgumentException.class,()->new CodeGroundedNativePipeline().build(source,NativeProjectionMode.AUTO,new CslMonitorConfiguration("1.0",SemanticProjectionProfile.CSL_OBSERVER_V2,new CslSchema("wrong-source-inheritance",wrong,List.of(),List.of(),List.of()),new RequirementModel(List.of()),List.of())));
        assertThrows(IllegalArgumentException.class,()->new CslStructuralPolicy.RoleCardinality("bad","team","base",2,1));
        assertThrows(IllegalArgumentException.class,()->new CslSchema.End("domain","part","2..1"));
        assertThrows(IllegalArgumentException.class,()->new CslMonitorConfiguration("1.0",SemanticProjectionProfile.CSL_OBSERVER_V1,new CslSchema("bad",types,List.of(),List.of(),List.of(),new CslStructuralPolicy(List.of(),List.of(),List.of(),true)),new RequirementModel(List.of()),List.of()));
    }
    @Test void associationAggregationCompositionKeepObservedDeletionPolicyExplicit()throws Exception {
        for(int kind=0;kind<3;kind++) {
            var relation=new CslSchema.Association("ownership","Ownership",List.of(new CslSchema.End("domain","whole","0..1"),new CslSchema.End("domain","parts","*")),kind);
            var pipeline=build(source(),CslStructuralPolicy.empty(),List.of(relation));var api=UseSystemApi.create(pipeline.state().system(),false);
            api.createObject("X","whole");api.createObject("X","part");api.createLink("Ownership","whole","part");
            assertTrue(pipeline.state().system().state().checkStructure(new java.io.PrintWriter(java.io.Writer.nullWriter())));
            api.deleteObject("whole");assertNotNull(pipeline.state().system().state().objectByName("part"));
            assertEquals(kind==2?"UNSUPPORTED_COMPOSITION_DESTRUCTION":"OBSERVED_ONLY",pipeline.model().model().getAssociation("Ownership").getAnnotationValue("CslAssociation","lifecycle"));
        }
    }
    @Test void nativeEmfM2SeparatesCoreClassifiersFromObserverPlayers()throws Exception {
        var pkg=org.tzi.use.plugins.jacamo.mapping.V2EcoreAuditTest.load(Files.readString(Path.of("Core/Metamodel/csl-monitor-v1/CSLMonitor.ecore")));
        assertEquals(org.eclipse.emf.common.util.Diagnostic.OK,org.eclipse.emf.ecore.util.Diagnostician.INSTANCE.validate(pkg).getSeverity());
        assertNotNull(pkg.getEClassifier("Entity"));assertNotNull(pkg.getEClassifier("Role"));assertNotNull(pkg.getEClassifier("OrgContext"));
        var observer=pkg.getESubpackages().getFirst();var atomic=(org.eclipse.emf.ecore.EClass)observer.getEClassifier("AtomicAgent");
        assertFalse(atomic.getEAllSuperTypes().contains(pkg.getEClassifier("Entity")));
        assertTrue(((org.eclipse.emf.ecore.EClass)observer.getEClassifier("ContextOccurrence")).getEAllSuperTypes().contains(observer.getEClassifier("Player")));
    }
}
