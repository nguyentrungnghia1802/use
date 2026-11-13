package org.tzi.use.state;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class CslStateVerifierTest {
    @TempDir Path directory;
    private static final String CSL="""
        csl v4.0 Example {
          entity Gauge { attribute reading : Integer required mutable; }
          context Gauge inv Nonnegative: self.reading >= 0;
        }
        """;
    private static final String ISTAR="""
        istar StateGoals { agent Gauge {
          goal Safe : Maintain condition {[ self.reading >= 0 ]}
          goal Done : Achieve
          task ReachedTwo > Done pre {[ true ]} post {[ self.reading >= 2 ]}
          task NonnegativeObserved > Done pre {[ true ]} post {[ self.reading >= 0 ]}
        } }
        """;
    private Path write(String name,String text)throws Exception{Path path=directory.resolve(name);Files.writeString(path,text);return path;}
    private CslStateVerifier verifier()throws Exception {
        return new CslStateVerifier(write("mapping.xml","""
          <mapping xmlns="urn:use:jacamo-state-mapping:1">
            <attribute source="artifact:unrelated.CustomGauge/property:value" target="Gauge.reading"/>
          </mapping>
          """),write("example.csl",CSL),write("example.istar",ISTAR));
    }
    private CslStateVerifier.Snapshot snapshot(long sequence,int value){return new CslStateVerifier.Snapshot("run",sequence,true,List.of(new CslStateVerifier.Artifact("physical-1","unrelated.CustomGauge","some-name","/main",Map.of("value",value))));}
    @Test void nativeCslAndIStarReadOnlyProjectedStateAndRetainOriginalTaskHistory()throws Exception {
        try(var verifier=verifier()) {
            var first=verifier.update(snapshot(1,0));assertEquals("APPROVE",first.decision());
            assertTrue(first.elements().stream().anyMatch(e->e.element().equals("ReachedTwo")&&e.status().equals("PENDING")));
            var second=verifier.update(snapshot(2,2));assertEquals("APPROVE",second.decision());
            assertTrue(second.elements().stream().anyMatch(e->e.element().equals("Done")&&e.status().equals("FULFILLED")));
            var third=verifier.update(snapshot(3,1));assertTrue(third.elements().stream().anyMatch(e->e.element().equals("ReachedTwo")&&e.status().equals("FULFILLED")));
            var failed=verifier.update(snapshot(4,-1));assertEquals("HOLD_FAILURE",failed.decision());
            assertTrue(failed.checks().stream().anyMatch(c->c.kind().equals("CSL_INVARIANT")&&c.verdict().equals("FAIL")));
            assertTrue(failed.elements().stream().anyMatch(e->e.element().equals("Safe")&&e.status().equals("VIOLATED")));
        }
    }
    @Test void physicalInstancesAndLifetimesHaveIndependentMarkings()throws Exception {
        try(var verifier=verifier()) {
            verifier.update(snapshot(1,2));
            var second=new CslStateVerifier.Artifact("physical-2","unrelated.CustomGauge","same-name","/main",Map.of("value",0));
            var result=verifier.update(new CslStateVerifier.Snapshot("run",2,true,List.of(snapshot(1,2).artifacts().getFirst(),second)));
            assertEquals(2,result.state().size());
            assertEquals(1,result.elements().stream().filter(e->e.element().equals("ReachedTwo")&&e.status().equals("FULFILLED")).count());
            verifier.update(new CslStateVerifier.Snapshot("run",3,true,List.of()));
            var fresh=verifier.update(snapshot(4,0));assertFalse(fresh.elements().stream().anyMatch(e->e.element().equals("ReachedTwo")&&e.status().equals("FULFILLED")));
        }
    }
    @Test void incompleteCoverageCannotDeleteObjectsOrCertifyEmptyState()throws Exception {
        try(var verifier=verifier()) {
            verifier.update(snapshot(1,1));
            var held=verifier.update(new CslStateVerifier.Snapshot("run",2,false,List.of()));
            assertEquals("HOLD_UNVERIFIED",held.decision());assertEquals(1,held.state().size());
            assertEquals("APPROVE",verifier.update(snapshot(2,2)).decision());
        }
    }
    @Test void invalidAttributesAndReorderedOrForeignSnapshotsDoNotAdvanceState()throws Exception {
        try(var verifier=verifier()) {
            var initial=verifier.update(snapshot(1,1));
            var invalid=new CslStateVerifier.Artifact("physical-1","unrelated.CustomGauge","name","/main",Map.of("value","wrong type"));
            assertThrows(IllegalArgumentException.class,()->verifier.update(new CslStateVerifier.Snapshot("run",2,true,List.of(invalid))));
            assertThrows(IllegalArgumentException.class,()->verifier.update(snapshot(3,2)));
            assertThrows(IllegalArgumentException.class,()->verifier.update(new CslStateVerifier.Snapshot("another-run",2,true,List.of())));
            assertEquals(initial,verifier.update(snapshot(1,1)));
            assertThrows(IllegalArgumentException.class,()->verifier.update(snapshot(1,100)));
            assertEquals("APPROVE",verifier.update(snapshot(2,2)).decision());
        }
    }
    @Test void mappingCannotContainAnyTaskOrPlanLogic()throws Exception {
        var file=write("bad.xml","<mapping xmlns='urn:use:jacamo-state-mapping:1'><task source='some-plan' target='some-task'/></mapping>");
        assertThrows(IllegalArgumentException.class,()->AttributeMapping.read(file));
    }
    @Test void anIStarMaintainViolationHoldsEvenWhenEveryCslInvariantPasses()throws Exception {
        Path map=write("mapping.xml","<mapping xmlns='urn:use:jacamo-state-mapping:1'><attribute source='artifact:unrelated.CustomGauge/property:value' target='Gauge.reading'/></mapping>");
        try(var verifier=new CslStateVerifier(map,write("a.csl",CSL),write("a.istar",ISTAR.replace("condition {[ self.reading >= 0 ]}","condition {[ self.reading < 2 ]}")))) {
            assertEquals("APPROVE",verifier.update(snapshot(1,0)).decision());
            var result=verifier.update(snapshot(2,2));assertEquals("HOLD_FAILURE",result.decision());
            assertTrue(result.checks().stream().filter(c->c.kind().equals("CSL_INVARIANT")).allMatch(c->c.verdict().equals("PASS")));
            assertTrue(result.elements().stream().anyMatch(e->e.element().equals("Safe")&&e.status().equals("VIOLATED")));
        }
    }
    @Test void iStarCannotDependOnSourceStepSuccess()throws Exception {
        Path map=write("mapping.xml","<mapping xmlns='urn:use:jacamo-state-mapping:1'><attribute source='artifact:test.Gauge/property:v' target='Gauge.reading'/></mapping>");
        assertThrows(IllegalArgumentException.class,()->new CslStateVerifier(map,write("a.csl",CSL),write("a.istar",ISTAR.replace("self.reading >= 2","step.succeeded"))));
    }
}
