package org.tzi.use.plugins.jacamo.semantics.auction;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.jacamo.bridge.contract.*;
import org.jacamo.bridge.adapter.*;
import org.tzi.use.main.Session;
import org.tzi.use.plugins.jacamo.JaCaMoFacade;
import org.tzi.use.plugins.jacamo.semantics.mapping.xml.*;
import org.tzi.use.plugins.jacamo.semantics.evidence.*;
import org.tzi.use.plugins.jacamo.codegrounded.CodeGroundedNativePipeline;
import org.tzi.use.plugins.jacamo.codegrounded.use.NativeProjectionMode;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.NativeRuntimeProjector;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.NativeRuntimeReplay;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.NativeReplayStepController;
import static org.junit.jupiter.api.Assertions.*;

/** Portable production input and public import with recorded transport, not live TCP acceptance. */
class GenericProgramPublicationTest {
    @TempDir Path temp;
    @Test void launcherDerivedJcmBuildsAndReplaysFromOwnedBytesWithoutWeakeningCanonicalPins()throws Exception {
        var root=AuctionDeclarativeSpecificationTest.CASE;var original=AuctionDeclarativeSpecificationTest.inputs(root);
        for(var file:original.specification().root().child("sources").children("file")) {var target=temp.resolve(file.attr("path"));Files.createDirectories(target.getParent());Files.copy(root.resolve(file.attr("path")),target);}
        for(String file:List.of("auction-mapping.xml","auction_constraints_final.ocl"))Files.copy(root.resolve(file),temp.resolve(file));
        Path jcm=temp.resolve("auction.jcm");String canonical=Files.readString(jcm);int closing=canonical.lastIndexOf('}');
        String insertion="    platform: org.jacamo.bridge.adapter.JaCaMoBridgePlatform(\"port=12345\", \"secretFile="+temp.resolve("secret.hex").toString().replace('\\','/')+"\", \"jcmFile="+jcm.toString().replace('\\','/')+"\", \"distributionSha256="+"a".repeat(64)+"\")\r\n";
        Files.writeString(jcm,canonical.substring(0,closing)+insertion+canonical.substring(closing));
        try(var fixture=new AuctionLiveFixture()) {
            var source=new OfficialProjectAdapter().adapt(new OfficialProjectLoader().load(jcm),jcm);
            var inputs=AuctionDeclarativeSpecificationTest.inputs(temp);var program=new GenericMappingEngine().map(source,inputs);
            assertEquals(original.require("src-jcm").sha256(),inputs.require("src-jcm").sha256());assertNotEquals(inputs.require("src-jcm").sha256(),source.semanticContract().project().sourceDigest());
            assertNull(program.casePlan());assertNotNull(program.xmlPlan());
            var built=new CodeGroundedNativePipeline().buildCanonicalProgram(source,NativeProjectionMode.AUTO,program);
            try(var projector=new NativeRuntimeProjector(built,"staged-launcher",1,source.modelRevision(),temp.resolve("runtime"))) {
                var accepted=projector.coordinator().awaitEvaluation(projector.coordinator().latest(),15000);
                var replay=new NativeRuntimeReplay();Path recording=temp.resolve("recording");replay.exportBundle(projector,built.nativeResult().export().useText(),recording);
                Files.delete(jcm);Files.delete(temp.resolve("auction-mapping.xml"));Files.delete(temp.resolve("auction_constraints_final.ocl"));
                var restored=new SemanticProgramManifest().decode(new SemanticProgramManifest().encode(program)).program();assertEquals(program.hash(),restored.hash());
                var report=replay.replay(recording);assertTrue(report.complete(),report.diagnostics().toString());assertEquals(accepted.stateHash(),report.finalStateHash());assertEquals(accepted.resultHash(),report.finalResultHash());
            }
        }
    }
    @Test void importWaitsForTheFormalBootstrapCutWhenBufferedIngressEndsWithTrace()throws Exception {
        try(var fixture=new AuctionLiveFixture()) {
            var source=fixture.source;var frames=new ArrayList<>(AuctionImportTest.frames(source));
            var event=new RuntimeEvent("bootstrap-trace","auction-import-run",1,source.modelRevision(),"jason","jason:auction-import-test",1,java.time.Instant.EPOCH,
                RuntimeEventKind.ADDED,RuntimeFactKind.RUNTIME_EVENT,ProjectionStatus.EVIDENCE_ONLY,new BridgeEntityId("jason","runtime","trace","bootstrap","trace","one"),null,"","",Map.of(),Map.of("normalizedEventKind","SOURCE_TRACE_ADDED"),new SourceWatermark("jason:auction-import-test",1),Completeness.COMPLETE,List.of());
            frames.add(AuctionImportTest.frame(MessageType.RUNTIME_EVENT,source,ContractPayloads.event(event)));
            var configuration=new org.tzi.use.plugins.jacamo.bridge.BridgeConnectionConfig(java.net.URI.create("tcp://127.0.0.1:6553"),Path.of("test-secret.hex"),"1".repeat(64),Set.of("official.model","runtime.snapshot"),4*1024*1024,5000,64);
            var host=new Session();
            try(var facade=new org.tzi.use.plugins.jacamo.DefaultJaCaMoFacade(Path.of("."),org.tzi.use.plugins.jacamo.SemanticAuthority.BRIDGE,()->configuration,
                    ignored->new org.tzi.use.plugins.jacamo.bridge.RecordedBridgeTransport(frames),org.tzi.use.plugins.jacamo.PipelineMode.CODE_GROUNDED_NATIVE,host)) {
                facade.importProject(AuctionDeclarativeSpecificationTest.CASE.resolve("auction.jcm"),new JaCaMoFacade.ImportSelection("JACAMO_DECLARATIVE_XML",AuctionDeclarativeSpecificationTest.CASE.resolve("auction-mapping.xml"),AuctionDeclarativeSpecificationTest.CASE.resolve("auction_constraints_final.ocl")));
                assertTrue(host.hasSystem());var coordinator=AuctionImportTest.projector(facade).coordinator();
                assertEquals("TRACE_ONLY",coordinator.lastObservation().freshness());
                assertEquals("PINNED_NATIVE_CUT",CanonicalJson.object(coordinator.latest().semantic().observation().get("originalEngine")).get("evaluated"));
                assertFalse(coordinator.journal().hasGap());
            }
        }
    }
    @Test void portableProgramRebuildsOnlyFromFrozenSourceXmlAndOclAndRejectsTamper()throws Exception {
        try(var fixture=new AuctionLiveFixture()) {
            var program=new GenericMappingEngine().map(fixture.source,AuctionDeclarativeSpecificationTest.inputs(AuctionDeclarativeSpecificationTest.CASE));
            var codec=new SemanticProgramManifest();var bytes=codec.encode(program);var encoded=CanonicalJson.object(CanonicalJson.decode(bytes));
            assertEquals("1.2.0",encoded.get("schemaVersion"));assertTrue(encoded.containsKey("declarative"));assertFalse(encoded.containsKey("case"));
            var restored=codec.decode(bytes).program();assertNotNull(restored.xmlPlan());assertNull(restored.casePlan());assertEquals(program.hash(),restored.hash());
            for(String key:List.of("profile","rules","implementation","declarative","programHash","cslHash","goalHash","bindingHash","traceHash")) {
                var changed=new TreeMap<>(encoded);changed.put(key,Set.of("profile","rules","implementation","declarative").contains(key)?Map.of():"invalid");
                assertThrows(RuntimeException.class,()->codec.decode(CanonicalJson.encode(changed)),key);
            }
        }
    }
    @Test void publicImportFreezesThreeInputsWithoutAnyOracleAndPreservesOwnerOnInvalidReimport()throws Exception {
        var root=AuctionDeclarativeSpecificationTest.CASE;var original=AuctionDeclarativeSpecificationTest.inputs(root);
        for(var file:original.specification().root().child("sources").children("file")) {var target=temp.resolve(file.attr("path"));Files.createDirectories(target.getParent());Files.copy(root.resolve(file.attr("path")),target);}
        for(String file:List.of("auction-mapping.xml","auction_constraints_final.ocl"))Files.copy(root.resolve(file),temp.resolve(file));
        try(var fixture=new AuctionLiveFixture()) {
            var jcm=temp.resolve("auction.jcm");var source=new OfficialProjectAdapter().adapt(new OfficialProjectLoader().load(jcm),jcm);var host=new Session();
            try(var facade=AuctionImportTest.facade(source,host)) {
                var selection=new JaCaMoFacade.ImportSelection("JACAMO_DECLARATIVE_XML",temp.resolve("auction-mapping.xml"),temp.resolve("auction_constraints_final.ocl"));
                facade.importProject(jcm,selection);var system=host.system();var owner=AuctionImportTest.projector(facade).coordinator().originalEngine();var evidence=facade.semanticProgramEvidence();
                assertNotNull(system.model().getClass("Auction"));assertEquals(0,system.state().numObjects());assertTrue(evidence.containsKey("declarativeProgram"));assertFalse(evidence.containsKey("caseProgram"));assertEquals(selection,facade.importSelection());
                Files.writeString(selection.specification(),"unsupported changed mapping");Files.writeString(selection.ocl(),"changed authored contract");
                facade.resyncRuntime();assertSame(system,host.system());assertSame(owner,AuctionImportTest.projector(facade).coordinator().originalEngine());assertEquals(evidence,facade.semanticProgramEvidence());
                assertThrows(RuntimeException.class,()->facade.importProject(jcm,selection));assertSame(system,host.system());assertSame(owner,AuctionImportTest.projector(facade).coordinator().originalEngine());
                assertFalse(Files.exists(temp.resolve("auction_csl.csl")));assertFalse(Files.exists(temp.resolve("auction_istar_final.istar")));
            }
        }
    }
    @Test void declarativeProgramExportsAndReplaysCorrectlyWithStepVerificationAndRejectsDowngrade()throws Exception {
        try(var fixture=new AuctionLiveFixture()) {
            var program=new GenericMappingEngine().map(fixture.source,AuctionDeclarativeSpecificationTest.inputs(AuctionDeclarativeSpecificationTest.CASE));
            var built=new CodeGroundedNativePipeline().buildCanonicalProgram(fixture.source,NativeProjectionMode.AUTO,program);
            Path runDir=Files.createTempDirectory(temp,"declarative-run-");
            try(var projector=new NativeRuntimeProjector(built,"declarative-run-001",1,fixture.source.modelRevision(),runDir)) {
                var coordinator=projector.coordinator();
                var accepted=coordinator.awaitEvaluation(coordinator.latest(),15000);
                assertNotNull(accepted);
                Path recording=temp.resolve("declarative-recording");
                var replay=new NativeRuntimeReplay();
                replay.exportBundle(projector,built.nativeResult().export().useText(),recording);

                var manifest=CanonicalJson.object(CanonicalJson.decode(Files.readAllBytes(recording.resolve("manifest.json"))));
                assertEquals("3.3.0",manifest.get("schemaVersion"));
                var programManifest=CanonicalJson.object(CanonicalJson.decode(Files.readAllBytes(recording.resolve("program.json"))));
                assertEquals("1.2.0",programManifest.get("schemaVersion"));
                assertTrue(programManifest.containsKey("declarative"));

                var report=replay.replay(recording);
                assertTrue(report.complete(),report.diagnostics().toString());
                assertEquals(accepted.stateHash(),report.finalStateHash());
                assertEquals(accepted.resultHash(),report.finalResultHash());

                try(var bundle=replay.openBundle(recording)) {
                    assertTrue(bundle.canonical());
                    assertFalse(bundle.steps().isEmpty());
                }

                var host=new Session();
                try(var controller=new NativeReplayStepController(host)) {
                    controller.open(recording);
                    assertTrue(host.system().isReadOnly());
                    assertTrue(controller.status().total()>=0);
                }
                assertFalse(host.hasSystem());

                for(String invalidVersion:List.of("9.0.0","3.1.0","3.0.0","2.0.0")) {
                    var downgraded=new TreeMap<>(manifest);
                    downgraded.put("schemaVersion",invalidVersion);
                    Files.write(recording.resolve("manifest.json"),CanonicalJson.encode(downgraded));
                    assertFalse(replay.replay(recording).complete(),"A declarative recording must not be accepted by "+invalidVersion);
                }
            }
        }
    }
}
