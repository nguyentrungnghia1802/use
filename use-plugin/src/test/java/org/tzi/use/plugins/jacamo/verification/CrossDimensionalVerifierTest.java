package org.tzi.use.plugins.jacamo.verification;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.tzi.use.plugins.jacamo.extraction.StaticProjectImporter;
import org.tzi.use.plugins.jacamo.mapping.*;
import org.tzi.use.plugins.jacamo.materialization.*;
import org.tzi.use.plugins.jacamo.trace.*;
import org.tzi.use.plugins.jacamo.verification.profile.*;
class CrossDimensionalVerifierTest {
    @Test void sourceRelationsRequireExactTargetsAndMissingEvidenceIsExplicit() throws Exception {
        var semantic=new StaticProjectImporter().importProject(Path.of("src/test/resources/auction/auction.jcm")).model();
        var mapping=new MappingLoader().loadCanonical(Path.of("."));
        var plan=new VerificationSemanticLayer().apply(new TransformationPlanner().plan(semantic,mapping),new VerificationProfileLoader().loadActive(mapping)).transformation();
        var instances=new InstancePlanner().plan(semantic,mapping,plan);
        var system=new DirectUseBackend().materialize(new TextBackend().generate("test",plan,instances),instances).system();
        var trace=new TraceBuilder().build(semantic,mapping,plan,instances);
        var verifier=new CrossDimensionalVerifier();
        var all=verifier.verify(semantic,system,trace);
        assertFalse(all.isEmpty());
        assertTrue(all.stream().anyMatch(r->r.outcome()==VerificationOutcome.PASS));
        assertFalse(all.stream().anyMatch(r->r.outcome()==VerificationOutcome.FAIL || r.outcome()==VerificationOutcome.ERROR),all.toString());
        var link=instances.links().stream().filter(l->l.association().equals("Agent_artifacts_Artifact")).findFirst().orElseThrow();
        String identity="agentmetamodel::Agent#artifacts";
        assertTrue(trace.bySemanticId(identity).stream().filter(r -> r.targetKind().equals("ASSOCIATION")).count() > 1,
                "source membership and target-only order support are separately traced");
        var withoutMembership = new TraceIndex(trace.records().stream().filter(r -> !(r.sourceSemanticId().equals(identity)
                && r.targetKind().equals("ASSOCIATION") && r.projectionRuleId() == null)).toList());
        assertEquals(VerificationOutcome.ERROR, verifier.verifyBinding(system, withoutMembership, identity,
                link.sourceSemanticId(), link.targetSemanticId()).outcome(), "support association cannot replace source membership");
        assertEquals(VerificationOutcome.PASS,verifier.verifyBinding(system,trace,identity,link.sourceSemanticId(),link.targetSemanticId()).outcome());
        assertEquals(VerificationOutcome.SKIPPED,verifier.verifyBinding(system,trace,identity,link.sourceSemanticId(),null).outcome());
        assertEquals(VerificationOutcome.ERROR,verifier.verifyBinding(system,trace,identity,link.sourceSemanticId(),"same-name-wrong-owner").outcome());
        // Another valid trace with the same source display kind cannot stand in for this exact artifact.
        var expectedObject=system.state().objectByName(link.targetObject());
        system.state().createObject(expectedObject.cls(),"sameNameOtherOwner");
        var extra=new ArrayList<>(trace.records());
        extra.add(new TraceRecord("other","other-owner:auction1","object:sameNameOtherOwner","Artifact","OBJECT","test",null,null,"hash",null,TraceRecord.Status.PROJECTED));
        assertEquals(VerificationOutcome.FAIL,verifier.verifyBinding(system,new TraceIndex(extra),identity,link.sourceSemanticId(),"other-owner:auction1").outcome());
        system.state().deleteLink(system.model().getAssociation(link.association()),List.of(system.state().objectByName(link.sourceObject()),system.state().objectByName(link.targetObject())),null);
        assertEquals(VerificationOutcome.FAIL,verifier.verifyBinding(system,trace,identity,link.sourceSemanticId(),link.targetSemanticId()).outcome());
        var event=org.tzi.use.plugins.jacamo.runtime.RuntimeEvent.create("belief",java.time.Instant.EPOCH,1,org.tzi.use.plugins.jacamo.semantic.Dimension.AGENT,org.tzi.use.plugins.jacamo.runtime.RuntimeEventKind.BELIEF_ADDED,"agent",link.sourceSemanticId(),Map.of("belief","value(1)"),null);
        assertEquals(VerificationOutcome.SKIPPED,verifier.unsupportedRuntime("PROPERTY_TO_BELIEF_DELIVERY",event).outcome());
    }
}
