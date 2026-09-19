package org.tzi.use.plugins.jacamo.runtime;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.tzi.use.plugins.jacamo.trace.*;
class RuntimeMigrationTest {
    @Test void exactAdapterRejectsMissingWrongKindStaleAndWrongSemanticIdentity() {
        var record = new TraceRecord("t", "semantic", "object:renamed", "AlternateEntity", "OBJECT", "alternate", null, null, "hash", "runtime", TraceRecord.Status.RESOLVED);
        var adapter = new TraceRuntimeTargetAdapter(new TraceIndex(List.of(record)));
        var request = new RuntimeTargetResolver.Request("runtime","semantic","OBJECT");
        assertEquals("object:renamed",adapter.resolve(request).useId());
        assertEquals(adapter.resolve(request),adapter.resolve(request));
        assertSame(record,adapter.resolve(request).provenance());
        assertThrows(IllegalArgumentException.class,()->adapter.resolve(new RuntimeTargetResolver.Request("missing","semantic","OBJECT")));
        assertThrows(IllegalArgumentException.class,()->adapter.resolve(new RuntimeTargetResolver.Request("runtime","other","OBJECT")));
        assertThrows(IllegalArgumentException.class,()->adapter.resolve(new RuntimeTargetResolver.Request("runtime","semantic","ATTRIBUTE")));
        var stale = new TraceRecord("t","semantic","object:renamed","AlternateEntity","OBJECT","alternate",null,null,"hash","runtime",TraceRecord.Status.STALE);
        assertThrows(IllegalArgumentException.class,()->new TraceRuntimeTargetAdapter(new TraceIndex(List.of(stale))).resolve(request));
    }
    @Test void alternateAnchorsPreserveAllSourceRulesWithoutChangingPipeline() {
        var original = new RuntimeMappingLoader().loadDefault();
        var rules = original.rules().stream().map(r -> new RuntimeMapping.Rule(r.id(),r.runtime(),r.dimension(),r.eventKind(),r.authoritative(),r.identity(),r.correlationRequired(),r.payload(),r.action(),r.targetKind(),"alternate:"+r.targetKind(),r.traceRequired(),r.mutation(),r.checkpoint(),r.support(),r.evidence(),r.assumptions(),r.unsupportedConditions(),r.migrationRisk())).toList();
        var alternate = new RuntimeMapping(original.schemaVersion(),original.status(),"synthetic-alternate",rules);
        new RuntimeMappingValidator(r -> { if (!r.anchor().equals("alternate:"+r.targetKind())) throw new IllegalArgumentException(); }).validate(alternate);
        assertThrows(RuntimeMappingException.class,()->new RuntimeMappingValidator().validate(alternate));
        for(int i=0;i<rules.size();i++) {
            assertEquals(original.rules().get(i).action(),rules.get(i).action());
            assertEquals(original.rules().get(i).eventKind(),rules.get(i).eventKind());
        }
    }
    @Test void alternateVocabularyUsesSameSnapshotQueueTraceAndMutationMechanics() throws Exception {
        var errors = new java.io.StringWriter();
        var model = org.tzi.use.parser.use.USECompiler.compileSpecification(
            new java.io.ByteArrayInputStream("model Alternate\nclass Vessel\nattributes\n level : Integer\nend\n".getBytes(java.nio.charset.StandardCharsets.UTF_8)),
            "alternate.use", java.net.URI.create("memory:/alternate.use"), new java.io.PrintWriter(errors), new org.tzi.use.uml.mm.ModelFactory());
        assertNotNull(model, errors.toString());
        var system = new org.tzi.use.uml.sys.MSystem(model);
        system.state().createObject(model.getClass("Vessel"),"tank");
        var trace = new TraceIndex(List.of(
            new TraceRecord("o","semantic","object:tank","Vessel","OBJECT","alternate",null,null,"hash","runtime",TraceRecord.Status.RESOLVED),
            new TraceRecord("a","property","attribute:Vessel.level","Level","ATTRIBUTE","alternate",null,null,"hash",null,TraceRecord.Status.PROJECTED)));
        var engine = new RuntimeMutationEngine(system,trace,new RuntimeMappingLoader().loadDefault(),new TraceRuntimeTargetAdapter(trace));
        var now=java.time.Instant.now();
        var initial=RuntimeEvent.create("s",now,0,org.tzi.use.plugins.jacamo.semantic.Dimension.ENVIRONMENT,RuntimeEventKind.SET_ATTRIBUTE,"runtime","semantic",Map.of("attribute","level","valueType","INTEGER","value",1),null);
        var snapshot=new RuntimeSnapshot("alternate",now,0,List.of(initial),"alternate-fingerprint");
        engine.applySnapshot(snapshot);
        assertTrue(engine.compareSnapshot(snapshot).isEmpty());
        try(var queue=new OrderedRuntimeEventQueue(8,e -> assertEquals(MutationStatus.APPLIED,engine.apply(e).status()))) {
            queue.start();
            queue.submit(RuntimeEvent.create("delta",now,1,org.tzi.use.plugins.jacamo.semantic.Dimension.ENVIRONMENT,RuntimeEventKind.SET_ATTRIBUTE,"runtime","semantic",Map.of("attribute","level","valueType","INTEGER","value",2),null));
            queue.stopGracefully(java.time.Duration.ofSeconds(5));
            assertEquals(1,queue.metrics().processed());
        }
        assertEquals("2",system.state().objectByName("tank").state(system.state()).attributeValue(model.getClass("Vessel").attribute("level",true)).toString());
        assertFalse(engine.runtimeTrace().byEventId("delta").isEmpty());
        engine.eventStreamClosed();
        engine.applySnapshot(snapshot);
        assertTrue(engine.compareSnapshot(snapshot).isEmpty());
    }
}
