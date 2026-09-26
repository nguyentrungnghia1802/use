package org.tzi.use.plugins.jacamo.bridge;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.jacamo.bridge.adapter.OfficialProjectAdapter;
import org.jacamo.bridge.adapter.OfficialProjectLoader;
import org.junit.jupiter.api.Test;
import org.tzi.use.plugins.jacamo.mapping.ActiveBaseline;
import org.tzi.use.plugins.jacamo.mapping.TransformationPlanner;
import org.tzi.use.plugins.jacamo.materialization.DirectUseBackend;
import org.tzi.use.plugins.jacamo.materialization.InstancePlanner;
import org.tzi.use.plugins.jacamo.materialization.TextBackend;
import org.tzi.use.plugins.jacamo.trace.TraceBuilder;
import org.tzi.use.plugins.jacamo.constraint.ConstraintExtractor;
import org.tzi.use.plugins.jacamo.ocl.OclGenerator;
import org.tzi.use.plugins.jacamo.ocl.OclProfileLoader;
import org.tzi.use.plugins.jacamo.verification.ConstraintOrigin;
import org.tzi.use.plugins.jacamo.verification.ConstraintRegistry;
import org.tzi.use.plugins.jacamo.verification.DefaultVerificationService;
import org.tzi.use.plugins.jacamo.verification.VerificationOutcome;
import org.tzi.use.plugins.jacamo.verification.profile.VerificationProfileLoader;
import org.tzi.use.plugins.jacamo.verification.profile.VerificationSemanticLayer;

class CanonicalCasesBridgePipelineTest {
    private record Case(String name, Path jcm, String sha256) { }

    @Test void helloAuctionAndHouseUseOneOfficialGenericPipeline() throws Exception {
        Path hello = Path.of("src/test/resources/canonical-cases/hello-world/helloworld.jcm").toAbsolutePath().normalize();
        Path examples = Path.of("..", "..", "JaCaMo", "examples").toAbsolutePath().normalize();
        for (Case value : List.of(
                new Case("hello", hello, "c81d15c9aa80c6e75ee8ead017f8daaddb1038ec9cfbc80c6a3057bde10b4101"),
                new Case("auction", examples.resolve("auction/auction.jcm"), "c766fb0dc5fc6f4085cf6c1fc26d2df09229256c2e6138dfd4d44ef21fb7d2fb"),
                new Case("house-building", examples.resolve("house-building/house-building.jcm"), "c14ae6299b0d2e0034d7daaa233b9bf1b94a7b337aa39477ec865c52ca5fef08"))) {
            run(value);
        }
    }

    @Test void officialHelloPassesCoreAndAuthoredHelloOcl() throws Exception {
        Path jcm=Path.of("src/test/resources/canonical-cases/hello-world/helloworld.jcm").toAbsolutePath().normalize();
        var snapshot=new OfficialProjectAdapter().adapt(new OfficialProjectLoader().load(jcm),jcm);var semantic=new NativeSemanticAdapter().adapt(snapshot,jcm.getParent(),"hello").model();var baseline=new ActiveBaseline().packaged();
        var structure=new VerificationSemanticLayer().apply(new TransformationPlanner().plan(semantic,baseline.mapping()),new VerificationProfileLoader().loadActive(baseline.mapping())).transformation();var instances=new InstancePlanner().plan(semantic,baseline.mapping(),structure);
        var profiles=new OclProfileLoader();var core=profiles.loadCore();var authored=profiles.loadCase(jcm.getParent(),Path.of("verification/hello.ocl"));var constraints=new ConstraintExtractor().extract(semantic,structure,Map.of());var ocl=new OclGenerator().generate("hello_bridge",structure,constraints,List.of(core,authored));var baseText=new TextBackend().generate("hello_bridge",structure,instances);var direct=new DirectUseBackend().materialize(new TextBackend.GeneratedArtifacts(ocl.useModel(),baseText.initialCommands()),instances);var trace=new TraceBuilder().build(semantic,baseline.mapping(),structure,instances);var registry=ConstraintRegistry.load(direct.system().model(),ocl,List.of(ConstraintRegistry.profile(ConstraintOrigin.CORE,core),ConstraintRegistry.profile(ConstraintOrigin.CASE,authored)));var report=new DefaultVerificationService().runFullVerification(direct.system(),registry,trace);
        assertTrue(direct.structureValid(),direct.validationOutput());assertTrue(registry.descriptors().stream().anyMatch(d->d.origin()==ConstraintOrigin.CASE));assertFalse(report.results().stream().anyMatch(r->r.outcome()==VerificationOutcome.FAIL||r.outcome()==VerificationOutcome.ERROR),report.results().toString());
    }

    private void run(Case value) throws Exception {
        assertEquals(value.sha256(), HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(Files.readAllBytes(value.jcm()))), value.name());
        var official = new OfficialProjectLoader().load(value.jcm());
        var snapshot1 = new OfficialProjectAdapter().adapt(official, value.jcm());
        var snapshot2 = new OfficialProjectAdapter().adapt(new OfficialProjectLoader().load(value.jcm()), value.jcm());
        assertEquals(snapshot1.modelRevision(), snapshot2.modelRevision(), value.name());
        var semantic = new NativeSemanticAdapter().adapt(snapshot1, value.jcm().getParent(), value.name()).model();
        var baseline = new ActiveBaseline().packaged();
        var structure = new TransformationPlanner().plan(semantic, baseline.mapping());
        var instances = new InstancePlanner().plan(semantic, baseline.mapping(), structure);
        var text = new TextBackend().generate(value.name().replace('-', '_') + "_bridge", structure, instances);
        var direct = new DirectUseBackend().materialize(text, instances);
        assertTrue(direct.structureValid(), value.name() + ": " + direct.validationOutput());
        var trace = new TraceBuilder().build(semantic, baseline.mapping(), structure, instances);
        assertFalse(trace.records().isEmpty(), value.name());
        assertEquals(trace.records(), new TraceBuilder().build(semantic, baseline.mapping(), structure, instances).records(), value.name());
    }
}
