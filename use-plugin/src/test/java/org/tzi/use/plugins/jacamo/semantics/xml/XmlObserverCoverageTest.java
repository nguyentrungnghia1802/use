package org.tzi.use.plugins.jacamo.semantics.xml;

import java.util.*;
import org.jacamo.bridge.contract.Completeness;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tzi.use.api.*;
import org.tzi.use.plugins.jacamo.semantics.ocl.*;
import org.tzi.use.plugins.jacamo.semantics.evidence.SemanticVerdict;
import org.tzi.use.semantic.engine.use75.*;
import org.tzi.use.uml.sys.MObject;
import static org.junit.jupiter.api.Assertions.*;

/** Native invariant admission for generic observer records, including manual USE value changes. */
class XmlObserverCoverageTest {
    @TempDir java.nio.file.Path temp;
    final Map<String,Completeness> coverage=Map.of("cartago",Completeness.COMPLETE);
    record Fixture(XmlObserverProjectionTest helper,XmlRuntimeMappingTest.Fixture state,MObject observer) { }
    Fixture fixture()throws Exception {
        var helper=new XmlObserverProjectionTest();helper.temp=temp;var state=helper.helper().fixture(XmlObserverProjectionTest.RUNTIME,XmlObserverProjectionTest.OBSERVERS);
        state.engine().applyAuctionCut(helper.cut(helper.helper(),state,1,3,true,true),"run",1,List.of());
        var object=state.engine().system().state().allObjects().stream().filter(o->o.cls().name().equals("SignalView")).findFirst().orElseThrow();return new Fixture(helper,state,object);
    }
    @Test void exactObserverFieldsHaveIndependentCoverageAndCannotBeReplacedByManualNativeValues()throws Exception {
        var f=fixture();var engine=f.state().engine();var observer=f.observer();var resolver=new ConstraintCoverageResolver();
        try(var backend=new NativeOclBackend(engine.system().model(),"xml-test","generic-observer")) {
            var compiled=backend.compile(new OclContractSpec("reported",OclContractSpec.Kind.INVARIANT,observer.cls(),null,"self.current"));
            assertEquals("",resolver.unavailableSelf(compiled.expression(),engine,observer,coverage,false),"A real observer is owned by the observer projector, not by M0 instance rules");
            assertEquals(NativeOclOutcome.Status.PASS,backend.evaluate(compiled,UseStateReadView.pin(null,engine.system().state()),Map.of("self",observer.value()),"").status());
            var saved=engine.savepoint();UseSystemApi.create(engine.system(),false).setAttributeValue(observer.name(),"current","false");
            assertTrue(resolver.unavailableSelf(compiled.expression(),engine,observer,coverage,false).startsWith("ERROR:XML_OBSERVER_VALUE_DIFFERS"),"Native edits are not source observations");engine.rollback(saved);
            engine.applyAuctionCut(f.helper().cut(f.helper().helper(),f.state(),2,3,null,false),"run",1,List.of());
            assertFalse(resolver.unavailableSelf(compiled.expression(),engine,observer,coverage,false).isBlank());
            var missing=backend.compile(new OclContractSpec("missing",OclContractSpec.Kind.INVARIANT,observer.cls(),null,"self.current.oclIsUndefined()"));
            assertFalse(resolver.unavailableSelf(missing.expression(),engine,observer,coverage,false).isBlank(),"Undefined knowledge cannot become a verified true predicate about source state");
            var historical=backend.compile(new OclContractSpec("history",OclContractSpec.Kind.INVARIANT,observer.cls(),null,"self.ever"));
            assertEquals("",resolver.unavailableSelf(historical.expression(),engine,observer,coverage,false),"Qualified latched history remains distinct from current missing evidence");
        }
    }
    @Test void scopedNativeInvariantUsesGenericEvidenceWithoutDereferencingLegacyCasePlan()throws Exception {
        var f=fixture();var engine=f.state().engine();var inv=new UseModelApi(engine.system().model()).createInvariant("Observed","SignalView","self.current",false);
        var prepared=new SemanticInvariantEvaluator().prepareScoped("observed",inv,engine,coverage,false,Set.of(),"");
        assertEquals(1,prepared.bodies().size());assertEquals("",prepared.bodies().getFirst().unavailable());
        assertEquals(engine.canonicalProgram().xmlPlan().hash(),prepared.bodies().getFirst().witness().get("mappingPlanHash"));
        var results=SemanticInvariantEvaluator.evaluateScoped(prepared,engine.system().state());assertTrue(results.stream().anyMatch(r->r.kind().equals("INVARIANT_BODY")&&r.verdict()==SemanticVerdict.PASS));
    }
    @Test void navigationMustUseTheAcceptedObserverReferenceEvenWhenBothTargetsHaveQualifiedValues()throws Exception {
        var helper=new XmlObserverReferenceTest();helper.temp=temp;var f=helper.fixture(XmlObserverReferenceTest.VIEWS);var engine=f.nativeState().engine();
        f.projection().apply(f.port(),"epoch","cut",Map.of("flags",XmlObserverReferenceTest.rows("physical-one","one",true)));
        var link=XmlObserverReferenceTest.links(f).stream().filter(l->"physical-one".equals(f.port().metadata(l.linkedObjects().getFirst(),"xml:observer:subject"))).findFirst().orElseThrow();
        var duty=link.linkedObjects().getFirst();var accepted=link.linkedObjects().get(1);
        var other=engine.system().state().allObjects().stream().filter(o->o.cls().name().equals("ShipmentMonitor")&&"physical-two".equals(f.port().metadata(o,"xml:observer:subject"))).findFirst().orElseThrow();
        var resolver=new ConstraintCoverageResolver();
        try(var backend=new NativeOclBackend(engine.system().model(),"xml-test","observer-reference")) {
            var compiled=backend.compile(new OclContractSpec("reported",OclContractSpec.Kind.INVARIANT,duty.cls(),null,"self.monitor.ready"));
            assertEquals("",resolver.unavailableSelf(compiled.expression(),engine,duty,coverage,false));
            var saved=engine.savepoint();var api=UseSystemApi.create(engine.system(),false);
            api.deleteLink("Observer_activation_monitor",new String[]{duty.name(),accepted.name()});
            api.createLink("Observer_activation_monitor",duty.name(),other.name());
            assertTrue(resolver.unavailableSelf(compiled.expression(),engine,duty,coverage,false).startsWith("ERROR:XML_OBSERVER_REFERENCE_DIFFERS"),"An independently qualified target is not the accepted physical occurrence");
            engine.rollback(saved);assertEquals("",resolver.unavailableSelf(compiled.expression(),engine,duty,coverage,false));
            f.projection().apply(f.port(),"epoch","missing",Map.of("flags",new org.tzi.use.plugins.jacamo.semantics.mapping.xml.runtime.RuntimeTables.Result(List.of(),Completeness.PARTIAL,List.of())));
            var defined=backend.compile(new OclContractSpec("missing",OclContractSpec.Kind.INVARIANT,duty.cls(),null,"self.monitor.oclIsUndefined()"));
            assertFalse(resolver.unavailableSelf(defined.expression(),engine,duty,coverage,false).isBlank(),"Missing reference evidence cannot supply a verified predicate");
        }
    }
}
