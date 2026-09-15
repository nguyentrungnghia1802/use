package org.tzi.use.plugins.jacamo.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cartago.ArtifactId;
import cartago.CartagoEnvironment;
import cartago.Op;
import cartago.util.agent.CartagoBasicContext;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import jason.asSemantics.Agent;
import jason.asSemantics.Event;
import jason.asSemantics.Intention;
import jason.asSyntax.ASSyntax;
import jason.asSyntax.Trigger;
import moise.oe.GroupInstance;
import moise.oe.OE;
import moise.oe.OEAgent;
import moise.oe.SchemeInstance;
import moise.os.OSBuilder;
import moise.os.ns.NS;
import moise.os.ns.Norm;
import org.junit.jupiter.api.Test;
import org.tzi.use.plugins.jacamo.extraction.StaticProjectImporter;
import org.tzi.use.plugins.jacamo.mapping.MappingLoader;
import org.tzi.use.plugins.jacamo.mapping.TransformationPlanner;
import org.tzi.use.plugins.jacamo.materialization.DirectUseBackend;
import org.tzi.use.plugins.jacamo.materialization.InstancePlanner;
import org.tzi.use.plugins.jacamo.materialization.TextBackend;
import org.tzi.use.plugins.jacamo.semantic.JaCaMoSemanticModel;
import org.tzi.use.plugins.jacamo.semantic.MetamodelKind;
import org.tzi.use.plugins.jacamo.trace.TraceBuilder;
import org.tzi.use.plugins.jacamo.trace.TraceIndex;
import org.tzi.use.plugins.jacamo.trace.TraceRecord;
import org.tzi.use.plugins.jacamo.verification.profile.VerificationProfileLoader;
import org.tzi.use.plugins.jacamo.verification.profile.VerificationSemanticLayer;
import org.tzi.use.uml.ocl.value.BooleanValue;
import org.tzi.use.uml.ocl.value.UndefinedValue;

class LiveJaCaMoAuctionIntegrationTest {
    @Test
    void mirrorsRealJasonCartagoAndMoiseAuctionThenReconnectsWithFullResync() throws Exception {
        var imported = new StaticProjectImporter().importProject(
                Path.of("src/test/resources/auction/auction.jcm"));
        JaCaMoSemanticModel semantic = imported.model();
        var mapping = new MappingLoader().loadCanonical(Path.of("."));
        var baseline = new TransformationPlanner().plan(semantic, mapping);
        var structure = new VerificationSemanticLayer().apply(baseline,
                new VerificationProfileLoader().loadV1()).transformation();
        var instances = new InstancePlanner().plan(semantic, mapping, structure);
        var generated = new TextBackend().generate("auction", structure, instances);
        DirectUseBackend.Result direct = new DirectUseBackend().materialize(generated, instances);
        TraceIndex trace = new TraceBuilder().build(semantic, mapping, structure, instances);

        String agentSemantic = semanticId(semantic, MetamodelKind.Agent, "auctioneer");
        String artifactSemantic = semanticId(semantic, MetamodelKind.Artifact, "auction1");
        String organisationSemantic = semanticId(semantic, MetamodelKind.Organisation, "auction_org");
        String groupSemantic = semanticId(semantic, MetamodelKind.Group, "auction_group");
        String schemeSemantic = semanticId(semantic, MetamodelKind.Scheme, "auction_scheme");

        Agent jasonAgent = new Agent();
        jasonAgent.setConsiderToAddMIForThisAgent(false);
        jasonAgent.initAg();
        jasonAgent.getBB().add(ASSyntax.parseLiteral("auction_open"));
        JasonRuntimeConnector jason = new JasonRuntimeConnector("jason-live",
                Map.of("auctioneer", jasonAgent.getTS()), Map.of("auctioneer", agentSemantic));

        CartagoEnvironment environment = CartagoEnvironment.getInstance();
        environment.init();
        CartagoBasicContext context = new CartagoBasicContext("auctioneer");
        ArtifactId artifact = context.makeArtifact(context.getJoinedWspId("main"), "auction1",
                TestAuctionArtifact.class.getName());
        CartagoArtifactBinding artifactBinding = new CartagoArtifactBinding("/main", "auction1", artifactSemantic,
                Map.of("open", "open"), Map.of("closeAuction", "closeAuction", "placeBid", "placeBid"));
        CartagoRuntimeConnector cartago = new CartagoRuntimeConnector("cartago-live",
                new OfficialCartagoRuntimeAccess(environment), List.of(artifactBinding));

        OSBuilder os = new OSBuilder();
        os.addRootGroup("auction_group");
        os.addRole("auction_group", "auctioneer");
        os.addScheme("auction_scheme", "sell_item");
        os.addMission("auction_scheme", "run_auction", "sell_item");
        Norm norm = new Norm(os.getOS().getSS().getRoleDef("auctioneer"),
                os.getOS().getFS().findMission("run_auction"), os.getOS().getNS(), NS.OpTypes.obligation);
        norm.setId("n1");
        os.getOS().getNS().addNorm(norm);
        OE organisation = new OE(null, os.getOS());
        GroupInstance group = organisation.addGroup("auction_group", "auction_group");
        SchemeInstance scheme = organisation.startScheme("auction_scheme", "auction_scheme");
        scheme.addResponsibleGroup(group);
        OEAgent organisationalAgent = organisation.addAgent("auctioneer");
        organisationalAgent.adoptRole("auctioneer", group);
        organisationalAgent.commitToMission("run_auction", scheme);
        MoiseRuntimeBinding moiseBinding = new MoiseRuntimeBinding("auction_org", organisationSemantic,
                Map.of("auctioneer", agentSemantic), Map.of("auction_group", groupSemantic),
                Map.of("auction_scheme", schemeSemantic));
        MoiseRuntimeConnector moise = new MoiseRuntimeConnector("moise-live", organisation, moiseBinding);

        register(trace, agentSemantic, "jason:agent:auctioneer");
        register(trace, agentSemantic, moiseBinding.agentRuntimeId("auctioneer"));
        register(trace, artifactSemantic, artifactBinding.runtimeSourceId());
        register(trace, organisationSemantic, moiseBinding.organisationRuntimeId());
        register(trace, groupSemantic, moiseBinding.groupRuntimeId("auction_group"));
        register(trace, schemeSemantic, moiseBinding.schemeRuntimeId("auction_scheme"));

        CompositeRuntimeConnector composite = new CompositeRuntimeConnector("jacamo-live",
                List.of(jason, cartago, moise));
        RuntimeMirrorService mirror = new RuntimeMirrorService(composite,
                new RuntimeMutationEngine(direct.system(), trace), 64);
        try {
            mirror.connect(URI.create("jacamo://local/auction"));
            assertEquals(MirrorState.LIVE, mirror.state());
            assertTrue(openValue(direct, trace, artifactSemantic));

            context.doAction(artifact, new Op("closeAuction"));
            jasonAgent.getTS().getC().addEvent(new Event(
                    Trigger.parseTrigger("+bid_seen(item1)"), Intention.EmptyInt));
            scheme.getGoal("sell_item").setAchieved(organisationalAgent);
            moise.pollChanges();
            mirror.awaitIdle(Duration.ofSeconds(5));
            assertFalse(openValue(direct, trace, artifactSemantic));
            assertEquals(0, mirror.metrics().dropped());
            assertTrue(mirror.metrics().processed() >= 5);

            mirror.disconnect();
            assertEquals(MirrorState.STALE, mirror.state());
            context.doAction(artifact, new Op("removeOpen"));
            mirror.reconnectAndResync();
            assertEquals(MirrorState.LIVE, mirror.state());
            assertEquals(UndefinedValue.instance, attributeValue(direct, trace, artifactSemantic, "open"));
            assertFalse(mirror.lastSnapshotFingerprint().isBlank());
        } finally {
            mirror.close();
            environment.getController("/main").removeArtifact("auction1");
        }
    }

    private String semanticId(JaCaMoSemanticModel model, MetamodelKind kind, String name) {
        return model.elements().stream().filter(element -> element.kind() == kind && name.equals(element.name()))
                .map(element -> element.id().value()).findFirst().orElseThrow();
    }

    private void register(TraceIndex trace, String semanticId, String runtimeKey) {
        TraceRecord record = trace.bySemanticId(semanticId).stream()
                .filter(value -> "OBJECT".equals(value.targetKind())).findFirst().orElseThrow();
        trace.registerRuntimeKey(record.traceId(), runtimeKey);
    }

    private boolean openValue(DirectUseBackend.Result direct, TraceIndex trace, String artifactSemantic) {
        return ((BooleanValue) attributeValue(direct, trace, artifactSemantic, "open")).value();
    }

    private Object attributeValue(DirectUseBackend.Result direct, TraceIndex trace, String artifactSemantic,
                                  String attribute) {
        TraceRecord record = trace.bySemanticId(artifactSemantic).stream()
                .filter(value -> "OBJECT".equals(value.targetKind())).findFirst().orElseThrow();
        var object = direct.system().state().objectByName(record.targetUseId().substring("object:".length()));
        return object.state(direct.system().state()).attributeValue(object.cls().attribute(attribute, true));
    }
}
