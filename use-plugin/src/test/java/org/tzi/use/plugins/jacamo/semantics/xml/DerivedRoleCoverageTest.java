package org.tzi.use.plugins.jacamo.semantics.xml;

import java.util.*;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.jacamo.bridge.contract.Completeness;
import org.tzi.use.plugins.jacamo.semantics.ocl.*;
import org.tzi.use.plugins.jacamo.codegrounded.constraint.ExternalOclConstraintService;
import org.tzi.use.semantic.engine.use75.*;
import org.tzi.use.api.UseModelApi;
import org.tzi.use.uml.sys.MObject;
import static org.junit.jupiter.api.Assertions.*;

class DerivedRoleCoverageTest {
    @TempDir Path temp;
    M0RuleExecutorTest.Fixture fixture()throws Exception {var helper=new M0RuleExecutorTest();helper.temp=temp;return helper.fixture(M0RuleExecutorTest.RUNTIME,true);}
    MObject context(M0RuleExecutorTest.Fixture f,String name,String quality)throws Exception {
        var o=f.port().upsert("group:"+name,"Team",name);var p=f.port();
        for(var entry:Map.of("xml:m0:program",f.engine().canonicalProgramHash(),"xml:m0:quality","COMPLETE","xml:m0:population","COMPLETE","xml:m0:population-authority","CARTAGO_BOARD_INVENTORY_V1","xml:m0:cut","cut","xml:m0:roles-cut","cut","xml:m0:roles-quality",quality).entrySet())p.metadata(o,entry.getKey(),entry.getValue());
        return o;
    }
    @Test void partialDerivedRolesCannotBecomeFalseOrTrueAndCompleteRemovalStillFails()throws Exception {
        var f=fixture();var group=context(f,"one","INCOMPLETE");var resolver=new ConstraintCoverageResolver();
        try(var backend=new NativeOclBackend(f.engine().system().model(),"test","derived")) {
            var compiled=backend.compile(new OclContractSpec("count",OclContractSpec.Kind.GOAL,group.cls(),null,"self.Worker->size() = 1"));
            var visitor=ExternalOclConstraintService.dependencyVisitor();compiled.expression().processWithVisitor(visitor);
            assertTrue(visitor.navigationReads().stream().anyMatch(n->n.getDestination().association().name().equals("InContext")));
            assertEquals("XML_CURRENT_ROLE_MATERIALIZATION_INCOMPLETE",resolver.unavailableSelf(compiled.expression(),f.engine(),group,Map.of("cartago",Completeness.COMPLETE),false));
            f.port().metadata(group,"xml:m0:roles-quality","COMPLETE");
            assertEquals("",resolver.unavailableSelf(compiled.expression(),f.engine(),group,Map.of("cartago",Completeness.COMPLETE),false));
            assertEquals(NativeOclOutcome.Status.FAIL,backend.evaluate(compiled,UseStateReadView.pin(null,f.engine().system().state()),Map.of("self",group.value()),"").status());
            var worker=f.port().upsert("role:one","Worker","worker");f.port().connect("InContext",group,worker);
            assertEquals(NativeOclOutcome.Status.PASS,backend.evaluate(compiled,UseStateReadView.pin(null,f.engine().system().state()),Map.of("self",group.value()),"").status());
            f.port().metadata(group,"xml:m0:roles-quality","INCOMPLETE");
            assertFalse(resolver.unavailableSelf(compiled.expression(),f.engine(),group,Map.of("cartago",Completeness.COMPLETE),false).isBlank(),"A true native value also requires complete role evidence");
            f.port().metadata(group,"xml:m0:roles-quality","COMPLETE");f.port().disconnect("InContext",group,worker);
            assertEquals(NativeOclOutcome.Status.FAIL,backend.evaluate(compiled,UseStateReadView.pin(null,f.engine().system().state()),Map.of("self",group.value()),"").status());
        }
    }
    @Test void transitiveDerivationKeepsIteratorReceiverAndReadSetDependencies()throws Exception {
        var f=fixture();var a=context(f,"one","COMPLETE");context(f,"two","INCOMPLETE");
        var model=f.engine().system().model();var api=new UseModelApi(model);var derived=api.createAttribute("Team","workerCount","Integer");
        try(var backend=new NativeOclBackend(model,"test","nested")) {
            var diagnostics=new java.io.StringWriter();var symbols=new org.tzi.use.parser.Symtable();symbols.add("self",a.cls(),null);
            var body=org.tzi.use.parser.ocl.OCLCompiler.compileExpression(model,"self.Worker->size()","derive",new java.io.PrintWriter(diagnostics),symbols);assertNotNull(body,diagnostics.toString());derived.setDeriveExpression(body);
            var compiled=backend.compile(new OclContractSpec("all",OclContractSpec.Kind.GOAL,a.cls(),null,"Team.allInstances()->forAll(t | t.workerCount = 1)"));
            assertEquals("XML_CURRENT_ROLE_MATERIALIZATION_INCOMPLETE",new ConstraintCoverageResolver().unavailableSelf(compiled.expression(),f.engine(),a,Map.of("cartago",Completeness.COMPLETE,"moise:team",Completeness.COMPLETE),false),"The derived receiver t must not be rebound to outer self");
            var readset=org.tzi.use.plugins.jacamo.semantics.mapping.ocl.ObservationReadSet.compile(Map.of("all",compiled.expression()));
            assertTrue(readset.navigation().stream().anyMatch(r->r.get("association").equals("InContext")));
        }
    }
    @Test void preStateOnDerivedReadIsRecordedAndCannotBorrowCurrentRoleEvidence()throws Exception {
        var f=fixture();var group=context(f,"one","COMPLETE");var model=f.engine().system().model();
        try(var backend=new NativeOclBackend(model,"test","pre")) {
            var expression=backend.compile(new OclContractSpec("pre",OclContractSpec.Kind.TRANSITION,group.cls(),null,"self.Worker@pre->size() = 1")).expression();
            var visitor=ExternalOclConstraintService.dependencyVisitor();expression.processWithVisitor(visitor);
            assertTrue(visitor.scopedNavigationReads().stream().anyMatch(n->n.preState()&&!n.frames().isEmpty()));
            var reads=org.tzi.use.plugins.jacamo.semantics.mapping.ocl.ObservationReadSet.compile(Map.of("pre",expression));
            assertTrue(reads.navigation().stream().anyMatch(n->Boolean.TRUE.equals(n.get("preState"))));
            assertEquals("UNSUPPORTED:DERIVED_PRE_STATE_COVERAGE_REQUIRES_PRE_VIEW",new ConstraintCoverageResolver().unavailableSelf(expression,f.engine(),group,Map.of("cartago",Completeness.COMPLETE),false));
        }
    }
    @Test void cyclicDerivedAttributeIsRejectedWithoutRecursionOrFabricatedAvailability()throws Exception {
        var f=fixture();var group=context(f,"one","COMPLETE");var model=f.engine().system().model();var attribute=new UseModelApi(model).createAttribute("Team","loop","Boolean");
        var variables=new org.tzi.use.parser.Symtable();variables.add("self",group.cls(),null);
        var expression=org.tzi.use.parser.ocl.OCLCompiler.compileExpression(model,"self.loop","cycle",new java.io.PrintWriter(System.err),variables);assertNotNull(expression);attribute.setDeriveExpression(expression);
        assertEquals("UNSUPPORTED:DERIVED_ATTRIBUTE_DEPENDENCY_CYCLE_OR_DEPTH",new ConstraintCoverageResolver().unavailableSelf(expression,f.engine(),group,Map.of("cartago",Completeness.COMPLETE),false));
    }
}
