package org.tzi.use.plugins.jacamo.semantics.auction;

import java.net.URI;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import org.jacamo.bridge.contract.*;
import org.junit.jupiter.api.Test;
import org.tzi.use.main.Session;
import org.tzi.use.plugins.jacamo.*;
import org.tzi.use.plugins.jacamo.bridge.*;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.NativeRuntimeProjector;
import org.tzi.use.plugins.jacamo.semantics.mapping.auction.*;
import static org.junit.jupiter.api.Assertions.*;

/** The public import API with recorded transport; not the external TCP live gate. */
class AuctionImportTest {
    private static final String DISTRIBUTION="1".repeat(64);
    private static final List<Capability> CAPABILITIES=List.of(
            new Capability("official.model",CapabilityStatus.COMPLETE,List.of(),""),
            new Capability("runtime.snapshot",CapabilityStatus.COMPLETE,List.of(),""));

    @Test void explicitXmlSelectionPublishesOneRealCanonicalModelAndNoConfiguredInstances() throws Exception {
        try(var fixture=new AuctionLiveFixture()) {
            var session=new Session();
            try(var facade=facade(fixture.source,session)) {
                var request=new JaCaMoFacade.ImportSelection("AUCTION_OBSERVED_V1",Path.of("examples/auction/auction_jacamo_to_csl_rules.xml"));
                facade.importProject(Path.of("examples/auction/auction.jcm"),request);
                assertSame(session.system().model(),session.system().state().system().model());
                assertEquals(0,session.system().state().numObjects(),"Configured agents/requests are not M0 population");
                assertNotNull(session.system().model().getClass("Auction"));
                assertNotNull(session.system().model().getClass("AuctionOrg"));
                assertNotNull(session.system().model().getClass("AuctionMonitor"));
                assertEquals("service",session.system().model().getClass("Auction").operation("start",false).paramList().varDecl(0).name());
                assertEquals(5,session.system().model().prePostConditions().size());
                var evidence=facade.semanticProgramEvidence();
                assertEquals("DERIVED_AND_RESOURCE_V1",evidence.get("profile"));
                var caseEvidence=CanonicalJson.object(evidence.get("caseProgram"));
                assertEquals(fixture.bundle.hash(),caseEvidence.get("bundleHash"));
                assertEquals(fixture.specification.sourceHash(),caseEvidence.get("specificationHash"));
                assertEquals("AUCTION_XML/1.0",caseEvidence.get("inputFormat"));
                assertEquals(3,((List<?>)caseEvidence.get("actors")).size());
                assertEquals(6,((List<?>)caseEvidence.get("tasks")).size());
                var actors=(List<?>)caseEvidence.get("actors");
                var organization=actors.stream().map(CanonicalJson::object).filter(a->a.get("cell").equals("O_actor")).findFirst().orElseThrow();
                assertEquals("AuctionOrg",organization.get("declaration"));assertEquals("ORGANIZATION_VIEWPOINT",organization.get("kind"));
                assertEquals("SKIPPED",facade.runtimeVerificationResult().semantic().constraints().stream().filter(c->c.id().equals("AUCTION:OCCURRENCES")).findFirst().orElseThrow().verdict().name());
                assertEquals(request,facade.importSelection());
                assertFalse(facade.workflowStatus().startAvailable(),"No live mapping capability is granted by a static import");
            }
            assertTrue(session.hasSystem(),"Existing close policy retires the monitor and preserves the USE model for inspection");
        }
    }

    @Test void acceptedSpecificationIsFrozenForResyncAndRestartAndFailedSelectionPreservesOwner() throws Exception {
        try(var fixture=new AuctionLiveFixture()) {
            var directory=copyCase(fixture.bundle);var xml=directory.resolve("auction_jacamo_to_csl_rules.xml");
            var request=new JaCaMoFacade.ImportSelection("AUCTION_OBSERVED_V1",xml);var session=new Session();
            var incoming=new java.util.concurrent.atomic.AtomicReference<>(fixture.source);
            try(var facade=facade(incoming::get,session)) {
                facade.importProject(Path.of("examples/auction/auction.jcm"),request);
                var system=session.system();var evidence=facade.semanticProgramEvidence();var owner=projector(facade).coordinator().originalEngine();
                Files.writeString(xml,"<changed/>");
                facade.resyncRuntime();assertSame(system,session.system());assertSame(owner,projector(facade).coordinator().originalEngine());assertSame(evidence,facade.semanticProgramEvidence());
                assertThrows(IllegalArgumentException.class,()->facade.importProject(Path.of("examples/auction/auction.jcm"),request));
                assertSame(system,session.system());assertSame(owner,projector(facade).coordinator().originalEngine());
                assertFalse(Boolean.TRUE.equals(owner.metrics().get("closed")));
                // A failure after the TCP model frame must also precede old ingress revocation.
                incoming.set(wrongClassProof(fixture.source));
                var originalRequest=new JaCaMoFacade.ImportSelection("AUCTION_OBSERVED_V1",Path.of("examples/auction/auction_jacamo_to_csl_rules.xml"));
                assertThrows(IllegalArgumentException.class,()->facade.importProject(Path.of("examples/auction/auction.jcm"),originalRequest));
                assertSame(system,session.system());assertSame(owner,projector(facade).coordinator().originalEngine());assertSame(evidence,facade.semanticProgramEvidence());
                assertEquals("STALE",facade.runFullVerification().fingerprints().get("freshness"),"A rejected bridge model preserves ownership, not certification of the latest source");
                incoming.set(wrongClassProofWithNewRevision(fixture.source));
                var rejected=assertThrows(IllegalArgumentException.class,()->facade.importProject(Path.of("examples/auction/auction.jcm"),originalRequest));
                assertTrue(rejected.getMessage().contains("METHOD_HASH"),rejected.getMessage());
                assertSame(system,session.system());assertSame(owner,projector(facade).coordinator().originalEngine());assertSame(evidence,facade.semanticProgramEvidence());
                owner.checkpoint().get(5,java.util.concurrent.TimeUnit.SECONDS);
                incoming.set(fixture.source);facade.stopMonitorSession();assertSame(system,session.system());facade.restartMonitorSession();
                assertNotSame(system,session.system());assertEquals(evidence,facade.semanticProgramEvidence());assertEquals(request,facade.importSelection());
                assertTrue(Boolean.TRUE.equals(owner.metrics().get("terminated")));
            }
        }
    }

    @Test void xmlIsNotJsonAndOnlyTheSelectedVersionedBundleCanBeImported() throws Exception {
        try(var fixture=new AuctionLiveFixture()) {
            var session=new Session();
            try(var facade=facade(fixture.source,session)) {
                assertThrows(IllegalArgumentException.class,()->facade.importProject(Path.of("examples/auction/auction.jcm"),new JaCaMoFacade.ImportSelection("AUCTION_OBSERVED_V1",null)));
                assertThrows(IllegalArgumentException.class,()->facade.importProject(Path.of("examples/auction/auction.jcm"),new JaCaMoFacade.ImportSelection("USE4GOAL_OBSERVED_V1",Path.of("examples/auction/auction_jacamo_to_csl_rules.xml"))));
                var directory=copyCase(fixture.bundle);var manifest=directory.resolve(AuctionCaseBundle.MANIFEST);
                Files.writeString(manifest,Files.readString(manifest).replace("\"schemaVersion\": \"1.0.0\"","\"schemaVersion\": \"99.0\""));
                assertThrows(IllegalArgumentException.class,()->facade.importProject(Path.of("examples/auction/auction.jcm"),new JaCaMoFacade.ImportSelection("AUCTION_OBSERVED_V1",directory.resolve("auction_jacamo_to_csl_rules.xml"))));
                assertFalse(session.hasSystem());
            }
        }
    }

    @Test void configuredSelectionFreezesAcceptedXmlWithoutReopeningChangedFiles()throws Exception {
        String profileProperty=org.tzi.use.plugins.jacamo.semantics.profile.SemanticProjectionProfile.PROPERTY;
        String oldProfile=System.getProperty(profileProperty),oldXml=System.getProperty(AuctionStaticMappingAdapter.PROPERTY);
        try(var fixture=new AuctionLiveFixture()) {
            var xml=copyCase(fixture.bundle).resolve("auction_jacamo_to_csl_rules.xml");
            System.setProperty(profileProperty,"AUCTION_OBSERVED_V1");System.setProperty(AuctionStaticMappingAdapter.PROPERTY,xml.toString());
            try(var facade=facade(fixture.source,new Session())) {
                facade.importProject(Path.of("examples/auction/auction.jcm"));var evidence=facade.semanticProgramEvidence();
                Files.writeString(xml,"changed after admission");
                facade.resyncRuntime();assertSame(evidence,facade.semanticProgramEvidence());
                facade.stopMonitorSession();facade.restartMonitorSession();assertEquals(evidence,facade.semanticProgramEvidence());
                assertEquals(new JaCaMoFacade.ImportSelection("AUCTION_OBSERVED_V1",xml),facade.importSelection());
            }
        }finally {
            if(oldProfile==null)System.clearProperty(profileProperty);else System.setProperty(profileProperty,oldProfile);
            if(oldXml==null)System.clearProperty(AuctionStaticMappingAdapter.PROPERTY);else System.setProperty(AuctionStaticMappingAdapter.PROPERTY,oldXml);
        }
    }

    static Path copyCase(AuctionCaseBundle bundle)throws Exception {
        Path directory=Files.createTempDirectory(Files.createDirectories(Path.of("target/auction-import")),"case-").toAbsolutePath();
        Files.copy(Path.of("examples/auction").resolve(AuctionCaseBundle.MANIFEST),directory.resolve(AuctionCaseBundle.MANIFEST));
        for(var input:bundle.inputs().values()) {Path target=directory.resolve(input.path());assertTrue(target.normalize().startsWith(directory));Files.createDirectories(target.getParent());Files.write(target,bundle.require(input.id()).bytes());}
        return directory;
    }
    static NativeRuntimeProjector projector(DefaultJaCaMoFacade facade)throws Exception {
        var field=DefaultJaCaMoFacade.class.getDeclaredField("nativeWorkspace");field.setAccessible(true);Object workspace=field.get(facade);
        var projector=workspace.getClass().getDeclaredField("runtimeProjector");projector.setAccessible(true);return (NativeRuntimeProjector)projector.get(workspace);
    }
    @SuppressWarnings("unchecked") private static ModelSnapshot wrongClassProof(ModelSnapshot source)throws Exception {
        Map<String,Object> model=new com.fasterxml.jackson.databind.ObjectMapper().readValue(CanonicalJson.encode(ContractPayloads.model(source)),Map.class);
        alterProof(model);return ContractPayloads.model(model);
    }
    @SuppressWarnings("unchecked") private static ModelSnapshot wrongClassProofWithNewRevision(ModelSnapshot source)throws Exception {
        Map<String,Object> model=new com.fasterxml.jackson.databind.ObjectMapper().readValue(CanonicalJson.encode(ContractPayloads.model(wrongClassProof(source))),Map.class);
        model.put("modelRevision","changed-revision-with-invalid-method-proof");return ContractPayloads.model(model);
    }
    @SuppressWarnings("unchecked") private static void alterProof(Object value) {
        if(value instanceof List<?> list){list.forEach(AuctionImportTest::alterProof);return;}
        if(value instanceof Map<?,?> raw) {
            var map=(Map<String,Object>)raw;
            if(map.get("diagnostics") instanceof List<?> diagnostics&&diagnostics.contains("SOURCE_ACTION_CARTAGO_ARTIFACT_OPERATION"))map.put("sourceDigest","0".repeat(64));
            new ArrayList<>(map.values()).forEach(AuctionImportTest::alterProof);
        }
    }

    static DefaultJaCaMoFacade facade(ModelSnapshot model,Session session) {
        return facade(()->model,session);
    }
    static DefaultJaCaMoFacade facade(java.util.function.Supplier<ModelSnapshot> model,Session session) {
        var configuration=new BridgeConnectionConfig(URI.create("tcp://127.0.0.1:6553"),Path.of("test-secret.hex"),DISTRIBUTION,
                Set.of("official.model","runtime.snapshot"),4*1024*1024,5000,64);
        return new DefaultJaCaMoFacade(Path.of("."),SemanticAuthority.BRIDGE,()->configuration,
                ignored->new RecordedBridgeTransport(frames(model.get())),PipelineMode.CODE_GROUNDED_NATIVE,session);
    }
    static List<byte[]> frames(ModelSnapshot model) {
        String source="jason:auction-import-test";
        var marks=Map.of(source,new SourceWatermark(source,0));
        var runtime=new RuntimeSnapshot("auction-import-empty",model.modelRevision(),Instant.EPOCH,Instant.EPOCH,marks,marks,1,
                List.of(),Map.of(source,Completeness.COMPLETE),"f".repeat(64));
        return List.of(frame(MessageType.HANDSHAKE,model,Map.of("readOnly",true)),
                frame(MessageType.MODEL_SNAPSHOT,model,ContractPayloads.model(model)),
                frame(MessageType.RUNTIME_SNAPSHOT,model,ContractPayloads.runtime(runtime)));
    }
    private static byte[] frame(MessageType type,ModelSnapshot model,Map<String,Object> payload) {
        var distribution=new DistributionFingerprint("1.3.1",DISTRIBUTION,Map.of("jason","3.3.2","cartago","3.1","moise","1.1","npl","0.6.1"));
        return ContractCodec.encode(ContractEnvelope.create("1.0.0",type,"auction-import-test",distribution,
                model.sources().getFirst().id().scope(),model.modelRevision(),"auction-import-run",1,type.name(),Instant.EPOCH,
                CAPABILITIES,Completeness.COMPLETE,Map.of(),List.of(),payload));
    }
}
