package org.tzi.use.plugins.jacamo.semantics;

import java.nio.file.*;
import cartago.Op;
import org.jacamo.bridge.contract.CanonicalJson;
import org.junit.jupiter.api.Test;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.NativeRuntimeReplay;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.NativeReplayStepController;
import org.tzi.use.plugins.jacamo.semantics.evidence.SemanticProgramManifest;
import org.tzi.use.plugins.jacamo.semantics.evidence.SemanticJson;
import org.tzi.use.plugins.jacamo.semantics.mapping.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CanonicalReplayTest {
    @Test void programCodecRejectsWrongEngineProfileModelAndSourceWithoutFrameworkSerialization() throws Exception {
        var source=CanonicalGoalMappingTest.source();var program=new JaCaMoStaticMappingAdapter().map(source,JaCaMoMappingProfile.observedV1(),CanonicalGoalMappingTest.rules(source));
        var codec=new SemanticProgramManifest();var bytes=codec.encode(program);assertEquals(program.hash(),codec.decode(bytes).program().hash());
        for(String feature:List.of("schemaVersion","programHash","cslHash","goalHash","bindingHash","traceHash","implementation","profile","source","rules")) {
            var value=new TreeMap<>(CanonicalJson.object(CanonicalJson.decode(bytes)));
            value.put(feature,Set.of("implementation","profile","source","rules").contains(feature)?Map.of():"invalid");
            assertThrows(RuntimeException.class,()->codec.decode(CanonicalJson.encode(value)),feature);
        }
        assertFalse(new String(bytes,java.nio.charset.StandardCharsets.UTF_8).contains("java.io.ObjectOutputStream"));
    }
    @Test void originalProgramNativeAtPreAndContextualHistoryRoundTripInVersionedBundle() throws Exception {
        try(var f=new CanonicalRuntimeFixture(CslCounterArtifact.class,CanonicalGoalMappingTest::rules)) {
            f.settled();f.alice.doAction(f.artifact,new Op("increment"));f.await(()->f.value("p").equals("1"));f.settled();
            f.alice.doAction(f.artifact,new Op("increment"));f.await(()->f.value("p").equals("2"));f.settled();f.resync();f.settled();
            var expected=f.projector.coordinator().latest();var root=Files.createTempDirectory(Path.of("target"),"canonical-replay-");var replay=new NativeRuntimeReplay();
            replay.exportBundle(f.projector,f.built.nativeResult().export().useText(),root);
            var manifest=CanonicalJson.object(CanonicalJson.decode(Files.readAllBytes(root.resolve("manifest.json"))));assertEquals("3.0.0",manifest.get("schemaVersion"));
            assertTrue(Files.isRegularFile(root.resolve("program.json")));var report=replay.replay(root);assertTrue(report.complete(),report.diagnostics().toString());
            assertEquals(expected.stateHash(),report.finalStateHash());assertEquals(expected.resultHash(),report.finalResultHash());
            try(var bundle=replay.openBundle(root);var cursor=bundle.atStep(bundle.steps().size()-1)) {
                assertNotSame(f.projector.system(),cursor.projector().system());assertNotNull(cursor.projector().coordinator().originalEngine());
                assertEquals(expected.semantic().toMap(),cursor.projector().coordinator().latest().semantic().toMap());
                assertTrue(cursor.projector().coordinator().latest().semantic().requirements().isEmpty());
            }
            // Canonical navigation reconstructs on its worker and atomically
            // selects one read-only offline system; it never joins on EDT.
            var host=new org.tzi.use.main.Session();
            try(var controller=new NativeReplayStepController(host)) {
                controller.open(root);assertTrue(host.system().isReadOnly());
                while(controller.status().step()<controller.status().total())controller.next();
                assertEquals(expected.resultHash(),controller.verificationSnapshot().result().resultHash());
                var tail=controller.historyTail();assertTrue(tail.entries().stream().allMatch(e->e.ordinal()<tail.persistedEntries()));
                controller.previous();controller.reset();assertEquals(0,controller.status().step());
            }
            assertFalse(host.hasSystem());
            var reanalysis=new org.tzi.use.plugins.jacamo.codegrounded.runtime.NativeRuntimeReanalysis().analyze(root,"other.ocl","context Counter inv Other: true",Map.of(),root.resolveSibling(root.getFileName()+"-analysis"));
            assertFalse(reanalysis.complete());assertTrue(reanalysis.diagnostics().toString().contains("REANALYSIS_CANONICAL_PROFILE_REQUIRES_SEPARATE_VERSIONED_POLICY"));
            var programBytes=Files.readAllBytes(root.resolve("program.json"));var manifestBytes=Files.readAllBytes(root.resolve("manifest.json"));
            var program=new TreeMap<>(CanonicalJson.object(CanonicalJson.decode(programBytes)));program.put("implementation",Map.of());
            Files.write(root.resolve("program.json"),CanonicalJson.encode(program));rehash(root,"program.json");
            assertTrue(replay.replay(root).diagnostics().toString().contains("REPLAY_ENGINE_GRAMMAR_BACKEND_INCOMPATIBLE"));
            Files.write(root.resolve("program.json"),programBytes);Files.write(root.resolve("manifest.json"),manifestBytes);
            Files.writeString(root.resolve("baseline.cmd"),"-- missing actual native baseline\n");rehash(root,"baseline.cmd");
            assertTrue(replay.replay(root).diagnostics().toString().contains("BASELINE_STATE_MISMATCH"));
        }
    }
    @Test void gapAndProducerResetRetainQualityAndResetHistoryWithNoBooleanCoercion() throws Exception {
        try(var f=new CanonicalRuntimeFixture(CslCounterArtifact.class,CanonicalGoalMappingTest::rules)) {
            f.settled();f.alice.doAction(f.artifact,new Op("increment"));f.await(()->f.value("p").equals("1"));f.settled();
            f.projector.coordinator().coverageGap("recorded-disconnect");f.settled();f.resync();f.settled();
            var root=Files.createTempDirectory(Path.of("target"),"canonical-gap-replay-");var replay=new NativeRuntimeReplay();replay.exportBundle(f.projector,f.built.nativeResult().export().useText(),root);
            var report=replay.replay(root);assertFalse(report.complete());assertEquals(List.of("REPLAY_PARTIAL_COVERAGE_GAP"),report.diagnostics());
            assertEquals(f.projector.coordinator().latest().resultHash(),report.finalResultHash());
            f.alice.doAction(f.artifact,new Op("set",0));f.await(()->f.value("p").equals("0"));f.settled();f.target.set(null);
            f.projector.applySnapshot(f.captures.capture(f.model.modelRevision(),3).snapshot(),"next-run",2);f.settled();
            root=Files.createTempDirectory(Path.of("target"),"canonical-reset-replay-");replay.exportBundle(f.projector,f.built.nativeResult().export().useText(),root);
            report=replay.replay(root);assertFalse(report.complete(),"A later reset does not erase the recorded earlier gap");assertEquals(f.projector.coordinator().latest().resultHash(),report.finalResultHash());
        }
    }
    @Test void realZeroToTwoInvocationFailureReplaysWithoutChangingHistoricalTaskToViolation() throws Exception {
        try(var f=new CanonicalRuntimeFixture(CslCounterArtifact.class,source->{var base=CanonicalGoalMappingTest.rules(source);
            var contracts=base.contracts().stream().map(c->new MappingRuleSet.Contract(c.id(),c.kind(),c.contextDeclaration(),c.targetElementId(),c.body(),List.of(CslCounterArtifact.class.getName(),"incrementTwo",List.of(),"void"),c.variableTypes(),c.origin())).toList();
            return new MappingRuleSet(base.version(),base.id(),base.profileId(),base.profileVersion(),base.expectedSourceHash(),base.cslSource(),base.iStarSource(),base.bindings(),contracts,base.decisions(),base.options());})) {
            f.settled();f.alice.doAction(f.artifact,new Op("incrementTwo"));f.await(()->f.value("p").equals("2"));f.settled();f.resync();f.settled();
            assertEquals("PENDING",OriginalEngineSessionIntegrationTest.element(f,"increment").get("leafStatus"));
            var root=Files.createTempDirectory(Path.of("target"),"canonical-invocation-failure-");var replay=new NativeRuntimeReplay();replay.exportBundle(f.projector,f.built.nativeResult().export().useText(),root);
            var report=replay.replay(root);assertTrue(report.complete(),report.diagnostics().toString());assertEquals(f.projector.coordinator().latest().resultHash(),report.finalResultHash());
            var lines=Files.readAllLines(root.resolve("runtime.jsonl"));assertTrue(lines.stream().map(l->CanonicalJson.object(CanonicalJson.decode(l.getBytes(java.nio.charset.StandardCharsets.UTF_8)))).filter(e->e.get("kind").equals("ENGINE_RESULT")).map(e->CanonicalJson.object(e.get("result"))).map(e->CanonicalJson.object(e.get("semantic"))).flatMap(e->((List<?>)e.get("constraints")).stream()).map(CanonicalJson::object).anyMatch(e->e.get("kind").equals("INVOCATION_CONFORMANCE")&&e.get("verdict").equals("FAIL")));
            // Even with a recomputed outer hash/chain a missing PRE cannot be
            // supplied from an expected result or a fabricated before state.
            var rows=lines.stream().map(l->new TreeMap<>(CanonicalJson.object(CanonicalJson.decode(l.getBytes(java.nio.charset.StandardCharsets.UTF_8))))).filter(e->!e.get("kind").equals("OPERATION_PRE")).toList();
            assertTrue(rows.size()<lines.size());String previous="";var text=new StringBuilder();int ordinal=0;
            for(var row:rows){row.remove("entryHash");row.put("ordinal",ordinal++);row.put("previousHash",previous);previous=org.tzi.use.plugins.jacamo.codegrounded.constraint.ExternalOclConstraintService.sha256(CanonicalJson.encode(row));row.put("entryHash",previous);text.append(new String(CanonicalJson.encode(row),java.nio.charset.StandardCharsets.UTF_8)).append('\n');}
            Files.writeString(root.resolve("runtime.jsonl"),text);rehash(root,"runtime.jsonl");assertFalse(replay.replay(root).complete());
        }
    }
    static void rehash(Path root,String file)throws Exception {
        var manifest=new TreeMap<>(CanonicalJson.object(CanonicalJson.decode(Files.readAllBytes(root.resolve("manifest.json")))));
        var hashes=new TreeMap<>(CanonicalJson.object(manifest.get("files")));hashes.put(file,org.tzi.use.plugins.jacamo.codegrounded.constraint.ExternalOclConstraintService.sha256(Files.readAllBytes(root.resolve(file))));manifest.put("files",hashes);Files.write(root.resolve("manifest.json"),CanonicalJson.encode(manifest));
    }
}
