package org.tzi.use.plugins.jacamo.semantics.auction;

import java.nio.file.*;
import java.util.*;
import org.jacamo.bridge.contract.*;
import org.junit.jupiter.api.Test;
import org.tzi.use.plugins.jacamo.semantics.evidence.*;
import org.tzi.use.plugins.jacamo.semantics.mapping.auction.*;
import static org.junit.jupiter.api.Assertions.*;

class AuctionProgramReplayTest {
    @Test void extendedCaseRecordingCapturesTheRunsBudgetAndRejectsMetadataDowngrade()throws Exception {
        String property="use.jacamo.runtime.journal.maxBytes",previous=System.getProperty(property);
        try {
            System.setProperty(property,"402653184");
            try(var fixture=new AuctionCastFixture(1)) {
                // Export/replay must use the captured run budget, not this later ambient property.
                System.setProperty(property,"67108864");
                AuctionNativeOperationTest.invoke(fixture,0,fixture.org.live.actor,new cartago.Op("start","budgeted recording"),false);
                var coordinator=fixture.projector.coordinator();var accepted=coordinator.awaitEvaluation(coordinator.latest(),15000);
                assertEquals(402653184L,coordinator.journal().maxBytes());
                Path recording=Files.createTempDirectory(Files.createDirectories(Path.of("target/auction-implementation")),"E02-budgeted-recording-");
                var replay=new org.tzi.use.plugins.jacamo.codegrounded.runtime.NativeRuntimeReplay();
                replay.exportBundle(fixture.projector,new org.tzi.use.plugins.jacamo.codegrounded.use.NativeUseExporter().export(fixture.projector.system().model()).useText(),recording);
                var manifest=new TreeMap<>(CanonicalJson.object(CanonicalJson.decode(Files.readAllBytes(recording.resolve("manifest.json")))));
                assertEquals("3.2.0",manifest.get("schemaVersion"));assertEquals(402653184L,((Number)manifest.get("runtimeByteLimit")).longValue());
                var report=replay.replay(recording);assertTrue(report.complete(),report.diagnostics().toString());assertEquals(accepted.stateHash(),report.finalStateHash());assertEquals(accepted.resultHash(),report.finalResultHash());
                for(String version:List.of("3.1.0","3.0.0","2.0.0")) {
                    var downgraded=new TreeMap<>(manifest);downgraded.put("schemaVersion",version);Files.write(recording.resolve("manifest.json"),CanonicalJson.encode(downgraded));
                    var rejected=replay.replay(recording);assertFalse(rejected.complete());assertTrue(rejected.diagnostics().toString().contains("REPLAY_BUDGET_SCHEMA_MISMATCH"),rejected.diagnostics().toString());
                }
            }
        }finally{if(previous==null)System.clearProperty(property);else System.setProperty(property,previous);}
    }
    @Test void sourceClockBinaryTamperCannotBeReinterpretedEvenWithRecomputedOuterHashes()throws Exception {
        try(var fixture=new AuctionCastFixture(1,1,true)) {
            var coordinator=fixture.projector.coordinator();coordinator.awaitEvaluation(coordinator.latest(),15000);
            Path recording=Files.createTempDirectory(Files.createDirectories(Path.of("target/auction-implementation")),"V02-clock-recording-");
            var replay=new org.tzi.use.plugins.jacamo.codegrounded.runtime.NativeRuntimeReplay();
            replay.exportBundle(fixture.projector,new org.tzi.use.plugins.jacamo.codegrounded.use.NativeUseExporter().export(fixture.projector.system().model()).useText(),recording);
            assertTrue(replay.replay(recording).complete());
            assertTrue(Files.size(recording.resolve("runtime.jsonl"))<8L*1024*1024,"This test mutates only a bounded component recording");
            var rows=Files.readAllLines(recording.resolve("runtime.jsonl")).stream().map(line->CanonicalJson.object(mutable(CanonicalJson.decode(line.getBytes(java.nio.charset.StandardCharsets.UTF_8))))).toList();boolean changed=false;
            for(var row:rows)if(row.get("kind").equals("SNAPSHOT"))for(var raw:(List<?>)CanonicalJson.object(row.get("payload")).get("facts")) {
                var values=CanonicalJson.object(CanonicalJson.object(raw).get("values"));
                if(values.get(VerificationObservation.KEY) instanceof Map<?,?> observer&&"CLOCK".equals(observer.get("kind"))) {
                    var data=CanonicalJson.object(observer.get("data"));var proof=CanonicalJson.object(data.get("sourceProof"));
                    CanonicalJson.object(proof.get("nplArtifact")).put("artifactSha256","f".repeat(64));changed=true;
                }
            }
            assertTrue(changed,"A real source clock must be present, not a fabricated timestamp");
            String chain="";var text=new StringBuilder();for(var row:rows){row.remove("entryHash");row.put("previousHash",chain);chain=org.tzi.use.plugins.jacamo.semantics.evidence.SemanticJson.hash(row);row.put("entryHash",chain);text.append(new String(CanonicalJson.encode(row),java.nio.charset.StandardCharsets.UTF_8)).append('\n');}
            Files.writeString(recording.resolve("runtime.jsonl"),text);var manifest=CanonicalJson.object(mutable(CanonicalJson.decode(Files.readAllBytes(recording.resolve("manifest.json")))));
            CanonicalJson.object(manifest.get("files")).put("runtime.jsonl",org.tzi.use.plugins.jacamo.codegrounded.constraint.ExternalOclConstraintService.sha256(Files.readAllBytes(recording.resolve("runtime.jsonl"))));
            Files.write(recording.resolve("manifest.json"),CanonicalJson.encode(manifest));
            var rejected=replay.replay(recording);assertFalse(rejected.complete());assertTrue(rejected.diagnostics().toString().contains("AUCTION_CLOCK_BINARY"),rejected.diagnostics().toString());
        }
    }
    @Test void caseRecordingUsesItsOwnVersionAndReplaysActualNativeObservationAndOperationCuts()throws Exception {
        try(var fixture=new AuctionCastFixture(1)) {
            AuctionNativeOperationTest.invoke(fixture,0,fixture.org.live.actor,new cartago.Op("start","recorded Auction service"),false);
            AuctionNativeOperationTest.invoke(fixture,0,fixture.participants.getFirst(),new cartago.Op("bid",17.5),false);
            var coordinator=fixture.projector.coordinator();var accepted=coordinator.awaitEvaluation(coordinator.latest(),15000);
            Path recording=Files.createTempDirectory(Files.createDirectories(Path.of("target/auction-implementation")),"V02-case-recording-");
            var replay=new org.tzi.use.plugins.jacamo.codegrounded.runtime.NativeRuntimeReplay();
            replay.exportBundle(fixture.projector,new org.tzi.use.plugins.jacamo.codegrounded.use.NativeUseExporter().export(fixture.projector.system().model()).useText(),recording);
            var manifest=CanonicalJson.object(CanonicalJson.decode(Files.readAllBytes(recording.resolve("manifest.json"))));assertEquals("3.1.0",manifest.get("schemaVersion"));
            assertEquals("1.1.0",CanonicalJson.object(CanonicalJson.decode(Files.readAllBytes(recording.resolve("program.json")))).get("schemaVersion"));
            var report=replay.replay(recording);assertTrue(report.complete(),report.diagnostics().toString());assertEquals(accepted.stateHash(),report.finalStateHash());assertEquals(accepted.resultHash(),report.finalResultHash());
            try(var bundle=replay.openBundle(recording);var cursor=bundle.atStep(bundle.steps().size()-1)) {
                assertEquals(accepted.semantic().toMap(),cursor.projector().coordinator().latest().semantic().toMap());
            }
            var host=new org.tzi.use.main.Session();
            try(var controller=new org.tzi.use.plugins.jacamo.codegrounded.runtime.NativeReplayStepController(host)) {
                controller.open(recording);assertTrue(host.system().isReadOnly());assertTrue(controller.status().total()>0);
                controller.next();assertEquals(1,controller.status().step());controller.previous();assertEquals(0,controller.status().step());controller.reset();assertEquals(0,controller.status().step());
            }assertFalse(host.hasSystem());
            var originalManifest=Files.readAllBytes(recording.resolve("manifest.json"));
            for(String invalid:List.of("9.0.0","3.0.0","2.0.0")) {
                var changed=new TreeMap<>(manifest);changed.put("schemaVersion",invalid);Files.write(recording.resolve("manifest.json"),CanonicalJson.encode(changed));
                assertFalse(replay.replay(recording).complete(),"A case program must not be silently dispatched by "+invalid);
            }
            Files.write(recording.resolve("manifest.json"),originalManifest);
        }
    }
    @Test void versionedCaseManifestMustRebuildTheSameCanonicalProgramWithoutOriginalFiles()throws Exception {
        try(var fixture=new AuctionLiveFixture()) {
            Path copy=AuctionImportTest.copyCase(fixture.bundle);var selection=AuctionStaticMappingAdapter.read(copy.resolve("auction_jacamo_to_csl_rules.xml"));
            var program=new AuctionStaticMappingAdapter().map(fixture.source,selection);var codec=new SemanticProgramManifest();byte[] bytes=codec.encode(program);
            var body=CanonicalJson.object(CanonicalJson.decode(bytes));assertEquals("1.1.0",body.get("schemaVersion"),"Auction cannot be encoded as the generic-only 1.0 program");
            assertTrue(body.containsKey("case"),"The XML case specification/assets/derivation must be frozen, not inferred from JSON rule options");
            // Only this test-owned copied fixture is removed. The original
            // canonical case/reference sources and oracles remain untouched.
            for(var input:selection.bundle().inputs().values()){Path owned=copy.resolve(input.path()).normalize();assertTrue(owned.startsWith(copy));Files.delete(owned);}
            Files.delete(copy.resolve(AuctionCaseBundle.MANIFEST));
            var decoded=codec.decode(bytes);assertNotNull(decoded.program().casePlan());assertEquals(program.hash(),decoded.program().hash());assertEquals(program.cslHash(),decoded.program().cslHash());assertEquals(program.goalHash(),decoded.program().goalHash());
            assertEquals(program.casePlan().hash(),decoded.program().casePlan().hash());assertEquals(program.casePlan().bundle().hash(),decoded.program().casePlan().bundle().hash());
            for(var input:selection.bundle().inputs().values())assertArrayEquals(selection.bundle().require(input.id()).bytes(),decoded.program().casePlan().bundle().require(input.id()).bytes());
            for(String field:List.of("requiredCapabilities","manifest","assets","derivedJcm","jcmDerivation")) {
                var changed=new TreeMap<>(body);var spec=new TreeMap<>(CanonicalJson.object(body.get("case")));
                spec.put(field,field.equals("requiredCapabilities")?List.of("unreviewed.future.capability"):field.equals("assets")?List.of():Map.of());changed.put("case",spec);
                assertThrows(RuntimeException.class,()->codec.decode(CanonicalJson.encode(changed)),field);
            }
            for(String field:List.of("implementation","profile","rules","source","programHash","bindingHash","traceHash")) {
                var changed=new TreeMap<>(body);changed.put(field,Set.of("implementation","profile","rules","source").contains(field)?Map.of():"wrong");
                assertThrows(RuntimeException.class,()->codec.decode(CanonicalJson.encode(changed)),field);
            }
        }
    }
    private static Object mutable(Object value) {
        if(value instanceof Map<?,?> map){var result=new LinkedHashMap<String,Object>();map.forEach((key,item)->result.put((String)key,mutable(item)));return result;}
        if(value instanceof List<?> list)return new ArrayList<>(list.stream().map(AuctionProgramReplayTest::mutable).toList());
        return value;
    }
}
